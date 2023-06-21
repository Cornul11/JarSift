package nl.tudelft.cornul11.thesis.database;

import com.zaxxer.hikari.HikariDataSource;
import nl.tudelft.cornul11.thesis.file.LibraryMatchInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
    public List<LibraryMatchInfo> returnTopLibraryMatches(List<String> hashes) {
        String placeholders = String.join(", ", Collections.nCopies(hashes.size(), "?"));
        String query = "SELECT libraries.groupId, libraries.artifactId, libraries.version, COUNT(*) as count " +
                "FROM signatures " +
                "JOIN libraries ON signatures.jar_id = libraries.id " +
                "WHERE signatures.hash IN (" + placeholders + ") " +
                "GROUP BY libraries.groupId, libraries.artifactId, libraries.version";

        String hashesJoined = String.join(", ", hashes.stream()
                .map(hash -> "'" + hash + "'")
                .collect(Collectors.toList()));

        String actualQuery = "SELECT libraries.groupId, libraries.artifactId, libraries.version, COUNT(*) as count " +
                "FROM signatures " +
                "JOIN libraries ON signatures.jar_id = libraries.id " +
                "WHERE signatures.hash IN (" + hashesJoined + ") " +
                "GROUP BY libraries.groupId, libraries.artifactId, libraries.version";

        try (FileOutputStream fos = new FileOutputStream("query.sql")) {
            fos.write(actualQuery.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<LibraryMatchInfo> libraryHashesCount = new ArrayList<>();

        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            for (int i = 0; i < hashes.size(); i++) {
                statement.setString(i + 1, hashes.get(i));
            }

            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String resultGroupId = resultSet.getString("groupId");
                String resultArtifactId = resultSet.getString("artifactId");
                String resultVersion = resultSet.getString("version");
                int resultCount = resultSet.getInt("count");

                LibraryMatchInfo libraryMatchInfo = new LibraryMatchInfo(resultGroupId, resultArtifactId, resultVersion, resultCount);
                libraryHashesCount.add(libraryMatchInfo);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return libraryHashesCount;
    }

    @Override
    public void closeConnection() {
        if (ds != null) {
            ds.close();
            logger.info("Database connection closed.");
        }
    }
}