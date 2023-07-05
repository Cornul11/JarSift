package nl.tudelft.cornul11.thesis.corpus.database;

import com.zaxxer.hikari.HikariDataSource;
import nl.tudelft.cornul11.thesis.corpus.file.JarInfoExtractor;
import nl.tudelft.cornul11.thesis.corpus.file.LibraryMatchInfo;
import nl.tudelft.cornul11.thesis.corpus.model.Signature;
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