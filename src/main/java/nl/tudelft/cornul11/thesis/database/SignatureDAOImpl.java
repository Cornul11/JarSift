package nl.tudelft.cornul11.thesis.database;

import com.zaxxer.hikari.HikariDataSource;
import nl.tudelft.cornul11.thesis.file.ClassMatchInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SignatureDAOImpl implements SignatureDAO {
    private HikariDataSource ds;
    private static final Logger logger = LoggerFactory.getLogger(SignatureDAOImpl.class);

    public SignatureDAOImpl(HikariDataSource ds) {
        this.ds = ds;
    }

    @Override
    public int insertSignatures(List<DatabaseManager.Signature> signatures, String jarHash) {
        String insertLibraryQuery = "INSERT INTO libraries (groupId, artifactId, version, hash) VALUES (?, ?, ?, ?)";
        String insertSignatureQuery = "INSERT INTO signatures (filename, hash, jar_id) VALUES (?, ?, ?)";

        int totalRowsInserted = 0;

        try (Connection connection = ds.getConnection()) {
            connection.setAutoCommit(false);

            // Insert Library metadata
            PreparedStatement libraryStatement = connection.prepareStatement(insertLibraryQuery, Statement.RETURN_GENERATED_KEYS);
            DatabaseManager.Signature firstSignature = signatures.get(0);
            libraryStatement.setString(1, firstSignature.groupID());
            libraryStatement.setString(2, firstSignature.artifactId());
            libraryStatement.setString(3, firstSignature.version());
            libraryStatement.setString(4, jarHash);
            libraryStatement.executeUpdate();

            // Get generated key
            ResultSet generatedKeys = libraryStatement.getGeneratedKeys();
            if (generatedKeys.next()) {
                int libraryId = generatedKeys.getInt(1);

                // Prepare the statement for batch processing
                PreparedStatement signatureStatement = connection.prepareStatement(insertSignatureQuery);

                // Insert signatures
                for (DatabaseManager.Signature signature : signatures) {
                    signatureStatement.setString(1, signature.fileName());
                    signatureStatement.setString(2, signature.hash());
                    signatureStatement.setInt(3, libraryId);
                    signatureStatement.addBatch();
                }

                // Execute batch and get affected rows
                int[] affectedRows = signatureStatement.executeBatch();
                totalRowsInserted += Arrays.stream(affectedRows).sum();
            }

            connection.commit();
            connection.setAutoCommit(true);

            logger.info(totalRowsInserted + " signature row(s) inserted.");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return totalRowsInserted;
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
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement(checkQuery)) {
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
        try (Connection connection = ds.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(selectQuery)) {
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
        if (ds != null) {
            ds.close();
            logger.info("Database connection closed.");
        }
    }
}