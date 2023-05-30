package nl.tudelft.cornul11.thesis.database;

import nl.tudelft.cornul11.thesis.file.ClassMatchInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SignatureDAOImpl implements SignatureDAO {
    private Connection connection;
    private static final Logger logger = LoggerFactory.getLogger(SignatureDAOImpl.class);

    public SignatureDAOImpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public int insertSignature(List<DatabaseManager.Signature> signatures) {
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
            return totalRowsInserted;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public List<ClassMatchInfo> returnMatches(List<String> hashes) {
        List<ClassMatchInfo> matches = new ArrayList<>();

        if (hashes.isEmpty()) {
            return matches;
        }

        StringBuilder builder = new StringBuilder("SELECT * FROM signatures WHERE hash IN (");
        for (int i = 0; i < hashes.size(); i++) {
            builder.append("?");
            if (i != hashes.size() - 1) {
                builder.append(",");
            }
        }
        builder.append(")");

        String checkQuery = builder.toString();

        // run the query to the database
        try (PreparedStatement statement = connection.prepareStatement(checkQuery)) {
            for (int i = 0; i < hashes.size(); i++) {
                statement.setString(i + 1, hashes.get(i));
            }

            ResultSet results = statement.executeQuery();

            while (results.next()) {
                String resultFilename = results.getString("filename");
                String resultGroupId = results.getString("groupId");
                String resultArtifactId = results.getString("artifactId");
                String resultVersion = results.getString("version");

                logger.info("Found match for " + resultFilename + " in artifactId " + resultArtifactId + " version " + resultVersion);

                ClassMatchInfo classMatchInfo = new ClassMatchInfo(resultFilename, resultGroupId, resultArtifactId, resultVersion);
                matches.add(classMatchInfo);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return matches;
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