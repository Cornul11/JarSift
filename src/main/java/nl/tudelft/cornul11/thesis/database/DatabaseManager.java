package nl.tudelft.cornul11.thesis.database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import nl.tudelft.cornul11.thesis.file.JarFileClassMatchInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseManager {
    private static final String DATABASE_URL = "jdbc:sqlite:corpus.sqlite";
    private Connection connection;
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);


    private DatabaseManager() {
        try {
            connection = DriverManager.getConnection(DATABASE_URL);
            logger.info("Connected to the database.");
            createSchema();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static final class InstanceHolder {
        // singleton instance
        private static final DatabaseManager instance = new DatabaseManager();
    }

    public static DatabaseManager getInstance() {
        return InstanceHolder.instance;
    }

    public SignatureDao getSignatureDao() {
        return new SignatureDaoImpl(connection);
    }

    private void createSchema() {
        createSignaturesTable();
    }

    private void createSignaturesTable() {
        String createTableQuery = "CREATE TABLE IF NOT EXISTS signatures (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "filename TEXT NOT NULL, " + "hash TEXT NOT NULL, "
                + "library TEXT NOT NULL, "
                + "version TEXT NOT NULL)";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(createTableQuery);
            logger.info("Signatures table created or already exists.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


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

    public record Signature(int id, String fileName, String hash, String library, String version) {
    }

}
