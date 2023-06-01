package nl.tudelft.cornul11.thesis.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private HikariDataSource ds;
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);


    private DatabaseManager(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());

        ds = new HikariDataSource(hikariConfig);
        logger.info("Connected to the database.");
        createSchema();
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
        return new SignatureDAOImpl(ds);
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
        try (Connection connection = ds.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(createTableQuery);
            logger.info("Signatures table created or already exists.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public record Signature(int id, String fileName, String hash, String groupID, String artifactId, String version) {
    }

}