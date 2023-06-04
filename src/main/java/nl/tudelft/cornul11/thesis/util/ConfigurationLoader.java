package nl.tudelft.cornul11.thesis.util;

import nl.tudelft.cornul11.thesis.database.DatabaseConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigurationLoader {
    private static final String CONFIG_FILE_NAME = "config.properties";
    private Properties config;

    public ConfigurationLoader() {
        loadConfig();
    }

    public void loadConfig() {
        config = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
            config.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Error while loading config file: " + CONFIG_FILE_NAME, e);
        }
    }

    public DatabaseConfig getDatabaseConfig() {
        return new DatabaseConfig(
                config.getProperty("database.url"),
                config.getProperty("database.username"),
                config.getProperty("database.password"),
                config.getProperty("dataSource.cachePrepStmts"),
                config.getProperty("dataSource.prepStmtCacheSize"),
                config.getProperty("dataSource.prepStmtCacheSqlLimit"),
                config.getProperty("dataSource.useServerPrepStmts"),
                config.getProperty("dataSource.useLocalSessionState"),
                config.getProperty("dataSource.rewriteBatchedStatements"),
                config.getProperty("dataSource.cacheResultSetMetadata"),
                config.getProperty("dataSource.cacheServerConfiguration"),
                config.getProperty("dataSource.elideSetAutoCommits"),
                config.getProperty("dataSource.maintainTimeStats")
        );
    }
}