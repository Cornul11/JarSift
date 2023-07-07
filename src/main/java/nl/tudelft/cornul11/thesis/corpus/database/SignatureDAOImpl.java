package nl.tudelft.cornul11.thesis.corpus.database;

import com.zaxxer.hikari.HikariDataSource;
import nl.tudelft.cornul11.thesis.corpus.file.JarInfoExtractor;
import nl.tudelft.cornul11.thesis.corpus.file.LibraryMatchInfo;
import nl.tudelft.cornul11.thesis.corpus.model.Signature;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class SignatureDAOImpl implements SignatureDAO {
    private final HikariDataSource ds;
    private static final Logger logger = LoggerFactory.getLogger(SignatureDAOImpl.class);
    private final long startTime = System.currentTimeMillis();

    public SignatureDAOImpl(HikariDataSource ds) {
        this.ds = ds;
    }

    @Override
    public int insertLibrary(JarInfoExtractor jarInfoExtractor, long jarHash, long jarCrc, boolean isBrokenJar) {
        String insertLibraryQuery = "INSERT INTO libraries (group_id, artifact_id, version, jar_hash, jar_crc, is_uber_jar) VALUES (?, ?, ?, ?, ?, ?)";

        executeWithDeadlockRetry(connection -> {
            try (PreparedStatement libraryStatement = connection.prepareStatement(insertLibraryQuery, Statement.RETURN_GENERATED_KEYS)) {
                libraryStatement.setString(1, jarInfoExtractor.getGroupId());
                libraryStatement.setString(2, jarInfoExtractor.getArtifactId());
                libraryStatement.setString(3, jarInfoExtractor.getVersion());
                libraryStatement.setLong(4, jarHash);
                libraryStatement.setLong(5, jarCrc);
                libraryStatement.setBoolean(6, !isBrokenJar);
                libraryStatement.executeUpdate();

                logger.info("Library row inserted.");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        return 0;
    }

    @Override
    public int insertSignatures(List<Signature> signatures, long jarHash, long jarCrc) {
        String insertLibraryQuery = "INSERT INTO libraries (group_id, artifact_id, version, jar_hash, jar_crc, is_uber_jar) VALUES (?, ?, ?, ?, ?, ?)";
        String insertSignatureQuery = "INSERT INTO signatures (library_id, class_hash, class_crc) VALUES (?, ?, ?)";  // library_id is added here.

        AtomicInteger totalRowsInserted = new AtomicInteger();
        executeWithDeadlockRetry(connection -> {
            try {
                PreparedStatement libraryStatement = connection.prepareStatement(insertLibraryQuery, Statement.RETURN_GENERATED_KEYS);
                Signature firstSignature = signatures.get(0);
                libraryStatement.setString(1, firstSignature.getGroupID());
                libraryStatement.setString(2, firstSignature.getArtifactId());
                libraryStatement.setString(3, firstSignature.getVersion());
                libraryStatement.setLong(4, jarHash);
                libraryStatement.setLong(5, jarCrc);
                libraryStatement.setBoolean(6, false);
                libraryStatement.executeUpdate();

                ResultSet generatedKeys = libraryStatement.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int libraryId = generatedKeys.getInt(1);

                    PreparedStatement insertStatement = connection.prepareStatement(insertSignatureQuery);

                    for (Signature signature : signatures) {
                        insertStatement.setInt(1, libraryId);  // setting the library_id for each signature
                        insertStatement.setLong(2, signature.getHash());
                        insertStatement.setLong(3, signature.getCrc());
                        insertStatement.executeUpdate();

                        totalRowsInserted.getAndIncrement();
                    }
                }

                String rowsInserted = "\033[0;32m" + totalRowsInserted + "\033[0m";
                logger.info(rowsInserted + " signature row(s) inserted.");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        return totalRowsInserted.get();
    }
    @Override
    public List<LibraryMatchInfo> returnTopLibraryMatches(List<Long> hashes) {
        long startTime = System.currentTimeMillis();

        // Create placeholders for the IN clause
        String placeholders = String.join(", ", Collections.nCopies(hashes.size(), "?"));

        // Create the countQuery
        String countQuery = "SELECT library_id, COUNT(*) as total_count " +
                "FROM signatures " +
                "GROUP BY library_id";

        // Create the mainQuery
        String mainQuery = "SELECT libraries.group_id, libraries.artifact_id, libraries.version, COUNT(*) as matched_count, total_count_table.total_count " +
                "FROM signatures " +
                "JOIN libraries ON signatures.library_id = libraries.id " +
                "LEFT JOIN (" + countQuery + ") as total_count_table ON signatures.library_id = total_count_table.library_id " +
                "WHERE signatures.class_hash IN (" + placeholders + ") " +
                "GROUP BY libraries.group_id, libraries.artifact_id, libraries.version, total_count_table.total_count";

        List<LibraryMatchInfo> libraryHashesCount = new ArrayList<>();

        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement(mainQuery)) {
            for (int i = 0; i < hashes.size(); i++) {
                statement.setLong(i + 1, hashes.get(i));
            }

            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String resultGroupId = resultSet.getString("group_id");
                String resultArtifactId = resultSet.getString("artifact_id");
                String resultVersion = resultSet.getString("version");
                int resultMatchedCount = resultSet.getInt("matched_count");
                int resultTotalCount = resultSet.getInt("total_count");

                LibraryMatchInfo libraryMatchInfo = new LibraryMatchInfo(resultGroupId, resultArtifactId, resultVersion, resultMatchedCount, resultTotalCount);
                libraryHashesCount.add(libraryMatchInfo);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();
        logger.info("Top matches query took " + (endTime - startTime) / 1000.0 + " seconds.");

        return libraryHashesCount;
    }

    public void insertPluginInfo(Model model) {
        long startTime = System.currentTimeMillis();

        String insertLibraryQuery = "INSERT INTO oracle_libraries (group_id, artifact_id, version) VALUES (?, ?, ?)";
        String insertDependencyQuery = "INSERT INTO dependencies (group_id, artifact_id, version, scope) VALUES (?, ?, ?, ?)";
        String insertPluginQuery = "INSERT INTO plugins (group_id, artifact_id, version) VALUES (?, ?, ?)";
        String insertPluginConfigQuery = "INSERT INTO plugin_config (plugin_id, config_key, config_value) VALUES (?, ?, ?)";

        executeWithDeadlockRetry(connection -> {
            try {
                PreparedStatement libraryStatement = connection.prepareStatement(insertLibraryQuery, Statement.RETURN_GENERATED_KEYS);
                libraryStatement.setString(1, model.getGroupId());
                libraryStatement.setString(2, model.getArtifactId());
                libraryStatement.setString(3, model.getVersion());
                libraryStatement.executeUpdate();

                List<Dependency> dependencies = model.getDependencies();
                PreparedStatement dependencyStatement = connection.prepareStatement(insertDependencyQuery);
                for (Dependency dependency : dependencies) {
                    dependencyStatement.setString(1, dependency.getGroupId());
                    dependencyStatement.setString(2, dependency.getArtifactId());
                    dependencyStatement.setString(3, dependency.getVersion());
                    dependencyStatement.setString(4, dependency.getScope());
                    dependencyStatement.executeUpdate();
                }

                Build build = model.getBuild();
                if (build != null) {
                    List<Plugin> plugins = build.getPlugins();
                    for (Plugin plugin : plugins) {
                        PreparedStatement pluginStatement = connection.prepareStatement(insertPluginQuery, Statement.RETURN_GENERATED_KEYS);
                        pluginStatement.setString(1, plugin.getGroupId());
                        pluginStatement.setString(2, plugin.getArtifactId());
                        pluginStatement.setString(3, plugin.getVersion());
                        pluginStatement.executeUpdate();

                        ResultSet generatedKeys = pluginStatement.getGeneratedKeys();
                        if (generatedKeys.next()) {
                            int pluginId = generatedKeys.getInt(1);

                            Map<String, String> configValues = parsePluginConfig(plugin);
                            PreparedStatement configStatement = connection.prepareStatement(insertPluginConfigQuery);
                            for (Map.Entry<String, String> entry : configValues.entrySet()) {
                                configStatement.setInt(1, pluginId);
                                configStatement.setString(2, entry.getKey());
                                configStatement.setString(3, entry.getValue());
                                configStatement.executeUpdate();
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });

        long endTime = System.currentTimeMillis();
        logger.info("Inserting plugin info took " + (endTime - startTime) / 1000.0 + " seconds.");

    }

    public Map<String, String> parsePluginConfig(Plugin plugin) {
        Map<String, String> configValues = new HashMap<>();

        Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();
        if (config != null) {
            for (Xpp3Dom child : config.getChildren()) {
                String key = child.getName();
                String value = child.getValue();
                configValues.put(key, value);
            }
        }

        return configValues;
    }

    private void executeWithDeadlockRetry(Consumer<Connection> action) {
        boolean success = false;
        while (!success) {
            try (Connection connection = ds.getConnection()) {
                connection.setAutoCommit(false);
                action.accept(connection);
                connection.commit();
                connection.setAutoCommit(true);
                success = true;
            } catch (SQLException e) {
                if (e.getErrorCode() == 1213) { // 1213 = ER_LOCK_DEADLOCK
                    handleDeadlock();
                } else {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    private void handleDeadlock() {
        logger.error("Deadlock detected. Retrying...");
        try {
            // sleep for a random amount of time between 0.5 and 1 seconds
            Thread.sleep(500 + (int) (Math.random() * 500));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        }
    }

    public List<Long> getHashesForArtifactIdVersion(String artifactId, String version) {
        String mainQuery = "SELECT * FROM libraries l JOIN signatures s ON l.id = s.library_id " +
                "WHERE l.artifact_id = '" + artifactId + "' AND l.version = '" + version + "'";

        List<Long> libraryHashes = new ArrayList<>();

        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement(mainQuery)) {

            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                long resultHash = resultSet.getLong("class_hash");
                libraryHashes.add(resultHash);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return libraryHashes;
    }

    @Override
    public void closeConnection() {
        if (ds != null) {
            ds.close();
            logger.info("Total time spent in database: " + (System.currentTimeMillis() - startTime) / 1000 + " seconds.");
            logger.info("Database connection closed.");
        }
    }
}