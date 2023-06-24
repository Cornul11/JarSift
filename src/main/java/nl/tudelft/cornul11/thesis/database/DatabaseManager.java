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
        hikariConfig.addDataSourceProperty("cachePrepStmts", config.getCachePrepStmts());
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", config.getPrepStmtCacheSize());
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", config.getPrepStmtCacheSqlLimit());
        hikariConfig.addDataSourceProperty("useServerPrepStmts", config.getUseServerPrepStmts());
        hikariConfig.addDataSourceProperty("useLocalSessionState", config.getUseLocalSessionState());
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", config.getRewriteBatchedStatements());
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", config.getCacheResultSetMetadata());
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", config.getCacheServerConfiguration());
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", config.getElideSetAutoCommits());
        hikariConfig.addDataSourceProperty("maintainTimeStats", config.getMaintainTimeStats());

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
        createLibrariesTable();
        createSignaturesTable();
        createLibrarySignatureTable();
        addIndexes();
    }

    private void addIndexes() {
        String createHashIndexQuery = "CREATE INDEX idx_hash ON signatures (hash)";
        String createLibraryIdIndexQuery = "CREATE INDEX idx_library_id ON library_signature (library_id)";
        String createSignatureIdIndexQuery = "CREATE INDEX idx_signature_id ON library_signature (signature_id)";

        try (Connection connection = ds.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(createHashIndexQuery);
            statement.executeUpdate(createLibraryIdIndexQuery);
            statement.executeUpdate(createSignatureIdIndexQuery);
            logger.info("Indexes created on signatures table.");
        } catch (SQLException e) {
            logger.error("Error while creating indexes on signatures table.", e);
        }
    }

    private void createLibrariesTable() {
        String createTableQuery = "CREATE TABLE IF NOT EXISTS libraries (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, "
                + "groupId VARCHAR(255) NOT NULL, "
                + "artifactId VARCHAR(255) NOT NULL, "
                + "version VARCHAR(255) NOT NULL, "
                + "hash VARCHAR(255) NOT NULL)"; // TODO: change to INT or BIGINT

        try (Connection connection = ds.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(createTableQuery);
            logger.info("Libraries table created or already exists.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createSignaturesTable() {
        String createTableQuery = "CREATE TABLE IF NOT EXISTS signatures (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "hash VARCHAR(255) NOT NULL)";

        try (Connection connection = ds.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(createTableQuery);
            logger.info("Signatures table created or already exists.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createLibrarySignatureTable() {
        String createTableQuery = "CREATE TABLE IF NOT EXISTS library_signature (" +
                "library_id INT NOT NULL, " +
                "signature_id INT NOT NULL, " +
                "filename VARCHAR(511) NOT NULL, " +
                "FOREIGN KEY (library_id) REFERENCES libraries(id), " +
                "FOREIGN KEY (signature_id) REFERENCES signatures(id))";

        try (Connection connection = ds.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(createTableQuery);
            logger.info("Library-Signature table created or already exists.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public record Signature(int id, String fileName, String hash, String groupID, String artifactId, String version) {
    }

}