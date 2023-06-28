package nl.tudelft.cornul11.thesis.database;

import com.zaxxer.hikari.HikariDataSource;
import nl.tudelft.cornul11.thesis.file.LibraryMatchInfo;
import nl.tudelft.cornul11.thesis.model.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SignatureDAOImpl implements SignatureDAO {
    private final HikariDataSource ds;
    private static final Logger logger = LoggerFactory.getLogger(SignatureDAOImpl.class);

    public SignatureDAOImpl(HikariDataSource ds) {
        this.ds = ds;
    }

    @Override
    public int insertSignatures(List<Signature> signatures, String jarHash) {
        String insertLibraryQuery = "INSERT INTO libraries (groupId, artifactId, version, hash) VALUES (?, ?, ?, ?)";
        String findOrInsertSignatureQuery = "INSERT IGNORE INTO signatures (hash) VALUES (?)";
        String getSignatureIdQuery = "SELECT id FROM signatures WHERE hash = ?";
        String insertLibrarySignatureQuery = "INSERT INTO library_signature (library_id, signature_id, filename) VALUES (?, ?, ?)";

        int totalRowsInserted = 0;
        boolean success = false;

        while (!success) {
            try (Connection connection = ds.getConnection()) {
                connection.setAutoCommit(false);

                PreparedStatement libraryStatement = connection.prepareStatement(insertLibraryQuery, Statement.RETURN_GENERATED_KEYS);
                Signature firstSignature = signatures.get(0);
                libraryStatement.setString(1, firstSignature.getGroupID());
                libraryStatement.setString(2, firstSignature.getArtifactId());
                libraryStatement.setString(3, firstSignature.getVersion());
                libraryStatement.setString(4, jarHash);
                libraryStatement.executeUpdate();

                ResultSet generatedKeys = libraryStatement.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int libraryId = generatedKeys.getInt(1);

                    PreparedStatement findOrInsertStatement = connection.prepareStatement(findOrInsertSignatureQuery);
                    PreparedStatement librarySignatureStatement = connection.prepareStatement(insertLibrarySignatureQuery);

                    for (Signature signature : signatures) {
                        findOrInsertStatement.setString(1, signature.getHash());
                        findOrInsertStatement.executeUpdate();

                        PreparedStatement getSignatureIdStatement = connection.prepareStatement(getSignatureIdQuery);
                        getSignatureIdStatement.setString(1, signature.getHash());
                        ResultSet resultSet = getSignatureIdStatement.executeQuery();
                        if (resultSet.next()) {
                            int signatureId = resultSet.getInt(1);
                            librarySignatureStatement.setInt(1, libraryId);
                            librarySignatureStatement.setInt(2, signatureId);
                            librarySignatureStatement.setString(3, signature.getFileName());
                            librarySignatureStatement.executeUpdate();

                            totalRowsInserted++;
                        }
                    }
                }

                connection.commit();
                connection.setAutoCommit(true);

                logger.info(totalRowsInserted + " signature row(s) inserted.");

                success = true;
            } catch (SQLException e) {
                if (e.getErrorCode() == 1213) { // 1213 = ER_LOCK_DEADLOCK
                    logger.error("Deadlock detected. Retrying...");

                    try {
                        // sleep for a random amount of time between 1 and 2 seconds
                        Thread.sleep(1000 + (int) (Math.random() * 1000));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    e.printStackTrace();
                    break;
                }
            }
        }

        return totalRowsInserted;
    }

    @Override
    public List<LibraryMatchInfo> returnTopLibraryMatches(List<String> hashes) {
        String placeholders = String.join(", ", Collections.nCopies(hashes.size(), "?"));
        String query = "SELECT libraries.groupId, libraries.artifactId, libraries.version, COUNT(*) as matchedCount, " +
                "(SELECT COUNT(*) FROM library_signature WHERE library_signature.library_id = libraries.id) as totalCount " +
                "FROM library_signature " +
                "JOIN libraries ON library_signature.library_id = libraries.id " +
                "JOIN signatures ON library_signature.signature_id = signatures.id " +
                "WHERE signatures.hash IN (" + placeholders + ") " +
                "GROUP BY libraries.groupId, libraries.artifactId, libraries.version";

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
                int resultMatchedCount = resultSet.getInt("matchedCount");
                int resultTotalCount = resultSet.getInt("totalCount");

                LibraryMatchInfo libraryMatchInfo = new LibraryMatchInfo(resultGroupId, resultArtifactId, resultVersion, resultMatchedCount, resultTotalCount);
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