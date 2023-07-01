package nl.tudelft.cornul11.thesis.database;

import com.zaxxer.hikari.HikariDataSource;
import nl.tudelft.cornul11.thesis.file.JarInfoExtractor;
import nl.tudelft.cornul11.thesis.file.LibraryMatchInfo;
import nl.tudelft.cornul11.thesis.model.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    public int insertLibrary(JarInfoExtractor jarInfoExtractor, long jarHash, long jarCrc) {
        String insertLibraryQuery = "INSERT INTO libraries (groupId, artifactId, version, hash, crc, isUberJar) VALUES (?, ?, ?, ?, ?, ?)";

        executeWithDeadlockRetry(connection -> {
            try (PreparedStatement libraryStatement = connection.prepareStatement(insertLibraryQuery, Statement.RETURN_GENERATED_KEYS)) {
                libraryStatement.setString(1, jarInfoExtractor.getGroupId());
                libraryStatement.setString(2, jarInfoExtractor.getArtifactId());
                libraryStatement.setString(3, jarInfoExtractor.getVersion());
                libraryStatement.setLong(4, jarHash);
                libraryStatement.setLong(5, jarCrc);
                libraryStatement.setBoolean(6, true);
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
        String insertLibraryQuery = "INSERT INTO libraries (groupId, artifactId, version, hash, crc, isUberJar) VALUES (?, ?, ?, ?, ?, ?)";
        String insertSignatureQuery = "INSERT INTO signatures (hash) VALUES (?)";
        String insertLibrarySignatureQuery = "INSERT INTO library_signature (library_id, signature_id) VALUES (?, ?)";


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

                    PreparedStatement insertStatement = connection.prepareStatement(insertSignatureQuery, Statement.RETURN_GENERATED_KEYS);
                    PreparedStatement librarySignatureStatement = connection.prepareStatement(insertLibrarySignatureQuery);

                    for (Signature signature : signatures) {
                        insertStatement.setString(1, signature.getHash());
                        insertStatement.executeUpdate();

                        ResultSet signatureGeneratedKeys = insertStatement.getGeneratedKeys();
                        if (signatureGeneratedKeys.next()) {
                            int signatureId = signatureGeneratedKeys.getInt(1);
                            librarySignatureStatement.setInt(1, libraryId);
                            librarySignatureStatement.setInt(2, signatureId);
                            librarySignatureStatement.executeUpdate();

                            totalRowsInserted.getAndIncrement();
                        }
                    }
                }

                logger.info(totalRowsInserted + " signature row(s) inserted.");
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
        String countQuery = "SELECT library_id, COUNT(*) as totalCount " +
                "FROM library_signature " +
                "GROUP BY library_id";

        // Create the mainQuery
        String mainQuery = "SELECT libraries.groupId, libraries.artifactId, libraries.version, COUNT(*) as matchedCount, totalCountTable.totalCount " +
                "FROM library_signature " +
                "JOIN libraries ON library_signature.library_id = libraries.id " +
                "JOIN signatures ON library_signature.signature_id = signatures.id " +
                "LEFT JOIN (" + countQuery + ") as totalCountTable ON library_signature.library_id = totalCountTable.library_id " +
                "WHERE signatures.hash IN (" + placeholders + ") " +
                "GROUP BY libraries.groupId, libraries.artifactId, libraries.version, totalCountTable.totalCount";


        List<LibraryMatchInfo> libraryHashesCount = new ArrayList<>();

        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement(mainQuery)) {
            for (int i = 0; i < hashes.size(); i++) {
                statement.setLong(i + 1, hashes.get(i));
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

        long endTime = System.currentTimeMillis();
        logger.info("Top matches query took " + (endTime - startTime) / 1000.0 + " seconds.");

        return libraryHashesCount;
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

    @Override
    public void closeConnection() {
        if (ds != null) {
            ds.close();
            logger.info("Total time spent in database: " + (System.currentTimeMillis() - startTime) / 1000 + " seconds.");
            logger.info("Database connection closed.");
        }
    }
}