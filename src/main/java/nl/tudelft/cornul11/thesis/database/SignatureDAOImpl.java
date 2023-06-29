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

public class SignatureDAOImpl implements SignatureDAO {
    private final HikariDataSource ds;
    private static final Logger logger = LoggerFactory.getLogger(SignatureDAOImpl.class);
    private final long startTime = System.currentTimeMillis();

    public SignatureDAOImpl(HikariDataSource ds) {
        this.ds = ds;
    }

    @Override
    public int insertLibrary(JarInfoExtractor jarInfoExtractor, long jarHash) {
        String insertLibraryQuery = "INSERT INTO libraries (groupId, artifactId, version, hash, isUberJar) VALUES (?, ?, ?, ?, ?)";

        executeWithDeadlockRetry(connection -> {
            try (PreparedStatement libraryStatement = connection.prepareStatement(insertLibraryQuery, Statement.RETURN_GENERATED_KEYS)) {
                libraryStatement.setString(1, jarInfoExtractor.getGroupId());
                libraryStatement.setString(2, jarInfoExtractor.getArtifactId());
                libraryStatement.setString(3, jarInfoExtractor.getVersion());
                libraryStatement.setLong(4, jarHash);
                libraryStatement.setBoolean(5, true);
                libraryStatement.executeUpdate();

                logger.info("Library row inserted.");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        return 0;
    }

    @Override
    public int insertSignatures(List<Signature> signatures, long jarHash) {
        String insertLibraryQuery = "INSERT INTO libraries (groupId, artifactId, version, hash, isUberJar) VALUES (?, ?, ?, ?, ?) RETURNING id";
        String insertSignatureAndGetIdQuery = "INSERT INTO signatures (hash) VALUES (?) RETURNING id";
        String insertLibrarySignatureQuery = "INSERT INTO library_signature (library_id, signature_id) VALUES (?, ?)";

        AtomicInteger totalRowsInserted = new AtomicInteger();
        executeWithDeadlockRetry(connection -> {
            PreparedStatement libraryStatement = connection.prepareStatement(insertLibraryQuery);
            Signature firstSignature = signatures.get(0);
            libraryStatement.setString(1, firstSignature.getGroupID());
            libraryStatement.setString(2, firstSignature.getArtifactId());
            libraryStatement.setString(3, firstSignature.getVersion());
            libraryStatement.setLong(4, jarHash);
            libraryStatement.setBoolean(5, false);

            try (ResultSet generatedKeys = libraryStatement.executeQuery()) {
                if (generatedKeys.next()) {
                    int libraryId = generatedKeys.getInt(1);

                    PreparedStatement insertSignatureAndGetIdStatement = connection.prepareStatement(insertSignatureAndGetIdQuery);
                    PreparedStatement librarySignatureStatement = connection.prepareStatement(insertLibrarySignatureQuery);

                    for (Signature signature : signatures) {
                        insertSignatureAndGetIdStatement.setString(1, signature.getHash());

                        try (ResultSet resultSet = insertSignatureAndGetIdStatement.executeQuery()) {
                            if (resultSet.next()) {
                                int signatureId = resultSet.getInt(1);
                                librarySignatureStatement.setInt(1, libraryId);
                                librarySignatureStatement.setInt(2, signatureId);
                                librarySignatureStatement.executeUpdate();

                                totalRowsInserted.getAndIncrement();
                            }
                        }
                    }
                }

                logger.info(totalRowsInserted + " signature row(s) inserted.");
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

    private void executeWithDeadlockRetry(SQLConsumer<Connection> action) {
        boolean success = false;
        while (!success) {
            try (Connection connection = ds.getConnection()) {
                connection.setAutoCommit(false);
                action.accept(connection);
                connection.commit();
                connection.setAutoCommit(true);
                success = true;
            } catch (SQLException e) {
                if (e.getSQLState().equals("40P01")) { // 40P01 - postgres' deadlock
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
            // sleep for a random amount of time between 1 and 2 seconds
            Thread.sleep(1000 + (int) (Math.random() * 1000));
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