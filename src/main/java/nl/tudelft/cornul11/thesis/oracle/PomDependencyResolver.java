package nl.tudelft.cornul11.thesis.oracle;

import java.io.File;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.Arrays;

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;

import nl.tudelft.cornul11.thesis.corpus.database.DatabaseConfig;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;

public class PomDependencyResolver {

  ConfigurationLoader config = new ConfigurationLoader();
  DatabaseConfig databaseConfig = config.getDatabaseConfig();
  DatabaseManager databaseManager = DatabaseManager.getInstance(databaseConfig);
  private String m2Path;

  public static void main(String[] args) {
    String m2Path = "$HOME/.m2/";
    if (args.length == 1) {
      m2Path = args[0];
    }
    m2Path = m2Path.replace("$HOME", System.getProperty("user.home"));
    m2Path = m2Path.replace("~", System.getProperty("user.home"));
    m2Path = Path.of(m2Path).toAbsolutePath().toString();
    new PomDependencyResolver().run(m2Path);
  }

  private void run(String m2Path) {
    this.m2Path = m2Path;
    
    databaseManager.createTmpDependenciesTable();

    // walk on all pom.xml files in the m2Path
    File dir = new File(m2Path);
    getDirectoryListing(dir);
  }

  private void getDirectoryListing(File dir) {
    File[] directoryListing = dir.listFiles();
    if (directoryListing != null) {
      for (File child : directoryListing) {
        if (child.getName().endsWith(".pom")) {
          resolvePom(child.getAbsolutePath());
        } else if (child.isDirectory()) {
          getDirectoryListing(child);
        }
      }
    }
  }

  private void resolvePom(String absolutePath) {
    String insertDependencyQuery = "INSERT INTO tmp_dependencies (parent_library_id, library_id, group_id, artifact_id, version) VALUES (?, ?, ?, ?, ?)";
    String findLib = "SELECT * FROM libraries WHERE group_id = ? AND artifact_id = ? AND version = ?";

    // search repository in absolutePath
    String artifactM2Path = absolutePath.indexOf("/repository/") != -1
        ? absolutePath.substring(absolutePath.indexOf("/repository/") + 12)
        : absolutePath.replace(m2Path, "");
    String[] splittedPath = artifactM2Path.split("/");
    String groupId = Arrays.stream(splittedPath).limit(splittedPath.length - 3).reduce((a, b) -> a + "." + b).get();
    String artifactOd = splittedPath[splittedPath.length - 3];
    String version = splittedPath[splittedPath.length - 2];

    try (java.sql.Connection connection = databaseManager.getDataSource().getConnection()) {
      int parentLibraryId = -1;
      try (PreparedStatement statement = connection.prepareStatement(findLib)) {
        statement.setString(1, groupId);
        statement.setString(2, artifactOd);
        statement.setString(3, version);
        statement.execute();
        if (!statement.getResultSet().next()) {
          return;
        }
        parentLibraryId = statement.getResultSet().getInt("id");
      }

      MavenResolvedArtifact[] artifacts = Maven.configureResolver().workOffline().loadPomFromFile(absolutePath)
          .importRuntimeDependencies()
          .resolve().withTransitivity().asResolvedArtifact();
      try (PreparedStatement statement = connection.prepareStatement(insertDependencyQuery)) {
        for (MavenResolvedArtifact artifact : artifacts) {
          Integer libraryId = null;
          MavenCoordinate coordinate = artifact.getCoordinate();
          String dependencyGroupId = coordinate.getGroupId();
          String dependencyArtifactId = coordinate.getArtifactId();
          String dependencyVersion = coordinate.getVersion();

          try (PreparedStatement depStatement = connection.prepareStatement(findLib)) {
            depStatement.setString(1, dependencyGroupId);
            depStatement.setString(2, dependencyArtifactId);
            depStatement.setString(3, dependencyVersion);
            depStatement.execute();
            if (depStatement.getResultSet().next()) {
              libraryId = depStatement.getResultSet().getInt("id");
            }
          }

          statement.setInt(1, parentLibraryId);
          statement.setInt(2, libraryId);
          statement.setString(3, dependencyGroupId);
          statement.setString(4, dependencyArtifactId);
          statement.setString(5, dependencyVersion);
          statement.addBatch();
        }
        statement.executeBatch();
      }
    } catch (java.lang.IllegalArgumentException e) {
      // e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
