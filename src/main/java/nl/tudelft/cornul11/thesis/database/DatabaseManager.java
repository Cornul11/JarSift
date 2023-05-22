package nl.tudelft.cornul11.thesis.database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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

    private void createSchema() {
        createSignaturesTable();
    }

    public void insertSignature(String fileName, String hash, String library, String version) {
        String insertQuery = "INSERT INTO signatures (filename, hash, version, library) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insertQuery)) {
            statement.setString(1, fileName);
            statement.setString(2, hash);
            statement.setString(3, library);
            statement.setString(4, version);
            int rowsInserted = statement.executeUpdate();
            logger.info(rowsInserted + " signature row(s) inserted.");
        } catch (SQLException e) {
            if ("X0Y32".equals(e.getSQLState())) {
                createSignaturesTable();
                insertSignature(fileName, hash, library, version); // Retry the insert after creating the table
            } else {
                e.printStackTrace();
            }
        }
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

    public List<Signature> getAllSignatures() {
        List<Signature> signatureList = new ArrayList<>();
        String selectQuery = "SELECT id, filename, hash, library, version FROM signatures";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(selectQuery)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String fileName = resultSet.getString("filename");
                String hash = resultSet.getString("hash");
                String library = resultSet.getString("library");
                String version = resultSet.getString("version");
                Signature signature = new Signature(id, fileName, hash, library, version);
                signatureList.add(signature);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return signatureList;
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
