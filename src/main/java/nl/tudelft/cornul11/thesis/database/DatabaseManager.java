package nl.tudelft.cornul11.thesis.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private Connection connection;
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);


    private DatabaseManager(DatabaseConfig config) {
        try {
            connection = DriverManager.getConnection(config.getUrl(), config.getUsername(), config.getPassword());
            logger.info("Connected to the database.");
            createSchema();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static final class InstanceHolder {
        // singleton instance
        private static DatabaseManager instance;
    }

    public static DatabaseManager getInstance(DatabaseConfig config) {
        if (InstanceHolder.instance == null) {
            InstanceHolder.instance = new DatabaseManager(config);
        }
        return InstanceHolder.instance;
    }

    public SignatureDAO getSignatureDao() {
        return new SignatureDAOImpl(connection);
    }

    private void createSchema() {
        createSignaturesTable();
    }

    private void createSignaturesTable() {
        String createTableQuery = "CREATE TABLE IF NOT EXISTS signatures (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, "
                + "filename TEXT NOT NULL, " + "hash TEXT NOT NULL, "
                + "groupId TEXT NOT NULL, "
                + "artifactId TEXT NOT NULL, "
                + "version TEXT NOT NULL)";

        // TODO: maybe add INDEX on hash column for faster lookups
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(createTableQuery);
            logger.info("Signatures table created or already exists.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public record Signature(int id, String fileName, String hash, String groupID, String artifactId, String version) {
    }

}