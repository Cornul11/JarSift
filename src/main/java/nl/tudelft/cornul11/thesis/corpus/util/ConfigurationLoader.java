package nl.tudelft.cornul11.thesis.corpus.util;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseConfig;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigurationLoader {
    private static final String CONFIG_FILE_NAME = "config.properties";
    private Properties config;

    public ConfigurationLoader(String path) {
        loadConfig(path);
    }
    
    public ConfigurationLoader() {
        this(CONFIG_FILE_NAME);
    }

    public void loadConfig(String path) {
        config = new Properties();
        try (InputStream input = new FileInputStream(path)) {
            config.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Error while loading config file: " + CONFIG_FILE_NAME, e);
        }
    }
    public void loadConfig() {
        loadConfig(CONFIG_FILE_NAME);
    }

    public boolean ignoreUberJarSignatures() {
        return Boolean.parseBoolean(config.getProperty("ignoreUberJarSignatures"));
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
                config.getProperty("dataSource.maintainTimeStats"),
                config.getProperty("dataSource.maximumPoolSize"),
                config.getProperty("dataSource.connectionTimeout"),
                config.getProperty("dataSource.leakDetectionThreshold")
        );
    }

    public int getNumConsumerThreads() {
        return config.getProperty("numConsumerThreads") == null ? 10 : Integer.parseInt(config.getProperty("numConsumerThreads"));
    }

    public int getTotalJars() {
        return config.getProperty("totalJars") == null ? -1 : Integer.parseInt(config.getProperty("totalJars"));
    }
}