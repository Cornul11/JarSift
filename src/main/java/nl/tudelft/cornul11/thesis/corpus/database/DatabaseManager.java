package nl.tudelft.cornul11.thesis.corpus.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private final HikariDataSource ds;
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private DatabaseManager(DatabaseConfig config) {
        ds = new HikariDataSource(getHikariConfig(config));
        logger.info("Connected to the database.");
        createSchema();
    }

    public HikariDataSource getDataSource() {
        return ds;
    }

    public static HikariConfig getHikariConfig(DatabaseConfig config) {
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
        hikariConfig.setMaximumPoolSize(Integer.parseInt(config.getMaximumPoolSize()));
        hikariConfig.setConnectionTimeout(Long.parseLong(config.getConnectionTimeout()));
        hikariConfig.setIdleTimeout(60000);
        hikariConfig.setMaxLifetime(6000000);
        return hikariConfig;
    }

    public void createTmpDependenciesTable() {
        String createTableQuery = "CREATE TABLE IF NOT EXISTS tmp_dependencies (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "parent_library_id INT NOT NULL, " +
                "library_id INT, " +
                "group_id VARCHAR(255) NOT NULL, " +
                "artifact_id VARCHAR(255) NOT NULL," +
                "version VARCHAR(255) NOT NULL, " +
                "UNIQUE INDEX uindex (parent_library_id , library_id), " +
                "FOREIGN KEY (parent_library_id) REFERENCES libraries(id), " +
                "FOREIGN KEY (library_id) REFERENCES libraries(id))";

        try (Connection connection = ds.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(createTableQuery);
            logger.info("Temporary dependencies table created or already exists.");
        } catch (SQLException e) {
            logger.error("Error while creating temporary dependencies table.", e);
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
        return new SignatureDAOImpl(ds);
    }

    public SignatureDAO getSignatureDao(String dbmode) {
        return new SignatureDAOImpl(ds, dbmode);
    }

    private void createSchema() {
        createLibrariesTable();
        createSignaturesTable();
        createTmpDependenciesTable();
//        addIndexes();
    }

    private void addIndexes() {
        String createLibraryIdIndexQuery = "CREATE INDEX idx_library_id ON libraries (id)";
        String createSignatureIdIndexQuery = "CREATE INDEX idx_signature_library_id ON signatures (library_id)";
        String createSignatureHashIndexQuery = "CREATE INDEX idx_signature_class_hash ON signatures (class_hash)";

        try (Connection connection = ds.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(createLibraryIdIndexQuery);
            statement.executeUpdate(createSignatureIdIndexQuery);
            statement.executeUpdate(createSignatureHashIndexQuery);
            logger.info("Indexes created on signatures table.");
        } catch (SQLException e) {
            logger.error("Error while creating indexes on signatures table.", e);
        }
    }

    private void createOracleLibrariesTable() {
        String createTableQuery = "CREATE TABLE IF NOT EXISTS oracle_libraries (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, "
                + "group_id VARCHAR(255) NOT NULL, "
                + "artifact_id VARCHAR(255) NOT NULL, "
                + "version VARCHAR(255) NOT NULL,"
                + "using_maven_shade_plugin BOOLEAN NOT NULL,"
                + "is_an_uber_jar BOOLEAN NOT NULL)";

        try (Connection connection = ds.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(createTableQuery);
            logger.info("Oracle libraries table created or already exists.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createLibrariesTable() {
        String createTableQuery = "CREATE TABLE IF NOT EXISTS libraries (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, "
                + "group_id VARCHAR(255) NOT NULL, "
                + "artifact_id VARCHAR(255) NOT NULL, "
                + "version VARCHAR(255) NOT NULL, "
                + "jar_hash BIGINT NOT NULL, "
                + "jar_crc BIGINT NOT NULL,"
                + "is_uber_jar BOOLEAN NOT NULL,"
                + "disk_size INT NOT NULL,"
                + "total_class_files INT NOT NULL,"
                + "unique_signatures INT NOT NULL)";

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
                "library_id INT NOT NULL," +
                "class_hash BIGINT NOT NULL," +
                "class_crc BIGINT NOT NULL)";

        try (Connection connection = ds.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(createTableQuery);
            logger.info("Signatures table created or already exists.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}