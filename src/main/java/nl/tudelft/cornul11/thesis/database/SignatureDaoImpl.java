package nl.tudelft.cornul11.thesis.database;

import nl.tudelft.cornul11.thesis.file.JarFileClassMatchInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SignatureDaoImpl implements SignatureDao {
    private Connection connection;
    private static final Logger logger = LoggerFactory.getLogger(SignatureDaoImpl.class);

    public SignatureDaoImpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void insertSignature(List<DatabaseManager.Signature> signatures) {
        String insertQuery = "INSERT INTO signatures (filename, hash, groupId, artifactId, version) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insertQuery)) {
            connection.setAutoCommit(false);

            for (DatabaseManager.Signature signature : signatures) {
                statement.setString(1, signature.fileName());
                statement.setString(2, signature.hash());
                statement.setString(3, signature.groupID());
                statement.setString(4, signature.artifactId());
                statement.setString(5, signature.version());
                statement.addBatch();
            }

            int[] rowsInserted = statement.executeBatch();
            connection.commit();
            connection.setAutoCommit(true);

            int totalRowsInserted = Arrays.stream(rowsInserted).sum();

            logger.info(totalRowsInserted + " signature row(s) inserted.");
        } catch (SQLException e) {
                e.printStackTrace();
        }
    }

    @Override
    public List<JarFileClassMatchInfo> returnMatches(String hash) {
        List<JarFileClassMatchInfo> matches = new ArrayList<>();
        String checkQuery = "SELECT * FROM signatures WHERE hash = ?";
        // run the query to the database
        try (PreparedStatement statement = connection.prepareStatement(checkQuery)) {
            statement.setString(1, hash);
            ResultSet results = statement.executeQuery();

            while (results.next()) {
                String resultFilename = results.getString("filename");
                String resultGroupId = results.getString("groupId");
                String resultArtifactId = results.getString("artifactId");
                String resultVersion = results.getString("version");

                logger.info("Found match for " + resultFilename + " in artifactId " + resultArtifactId + " version " + resultVersion);

                JarFileClassMatchInfo jarFileClassMatchInfo = new JarFileClassMatchInfo(resultFilename, resultGroupId, resultArtifactId, resultVersion);
                matches.add(jarFileClassMatchInfo);
            }
            return matches;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<DatabaseManager.Signature> getAllSignatures() {
        List<DatabaseManager.Signature> signatureList = new ArrayList<>();
        String selectQuery = "SELECT id, filename, hash, groupId, artifactId, version FROM signatures";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(selectQuery)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String fileName = resultSet.getString("filename");
                String hash = resultSet.getString("hash");
                String groupId = resultSet.getString("groupId");
                String artifactId = resultSet.getString("artifactId");
                String version = resultSet.getString("version");
                DatabaseManager.Signature signature = new DatabaseManager.Signature(id, fileName, hash, groupId, artifactId, version);
                signatureList.add(signature);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return signatureList;
    }

    @Override
    public void closeConnection() {
        try {
            if (connection != null) {
                connection.close();
                logger.info("Database connection closed.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}