package nl.tudelft.cornul11.thesis.corpus.database;

public class DatabaseConfig {
    private final String url;
    private final String username;
    private final String password;
    private final String cachePrepStmts;
    private final String prepStmtCacheSize;
    private final String prepStmtCacheSqlLimit;
    private final String useServerPrepStmts;
    private final String useLocalSessionState;
    private final String rewriteBatchedStatements;
    private final String cacheResultSetMetadata;
    private final String cacheServerConfiguration;
    private final String elideSetAutoCommits;
    private final String maintainTimeStats;
    private final String maximumPoolSize;
    private final String connectionTimeout;
    private final String leakDetectionThreshold;

    public DatabaseConfig(String url, String username, String password, String cachePrepStmts, String prepStmtCacheSize, String prepStmtCacheSqlLimit, String useServerPrepStmts, String useLocalSessionState, String rewriteBatchedStatements, String cacheResultSetMetadata, String cacheServerConfiguration, String elideSetAutoCommits, String maintainTimeStats, String maximumPoolSize, String connectionTimeout, String leakDetectionThreshold) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.cachePrepStmts = cachePrepStmts;
        this.prepStmtCacheSize = prepStmtCacheSize;
        this.prepStmtCacheSqlLimit = prepStmtCacheSqlLimit;
        this.useServerPrepStmts = useServerPrepStmts;
        this.useLocalSessionState = useLocalSessionState;
        this.rewriteBatchedStatements = rewriteBatchedStatements;
        this.cacheResultSetMetadata = cacheResultSetMetadata;
        this.cacheServerConfiguration = cacheServerConfiguration;
        this.elideSetAutoCommits = elideSetAutoCommits;
        this.maintainTimeStats = maintainTimeStats;
        this.maximumPoolSize = maximumPoolSize;
        this.connectionTimeout = connectionTimeout;
        this.leakDetectionThreshold = leakDetectionThreshold;
    }

    public String getCachePrepStmts() {
        return cachePrepStmts;
    }

    public String getPrepStmtCacheSize() {
        return prepStmtCacheSize;
    }

    public String getPrepStmtCacheSqlLimit() {
        return prepStmtCacheSqlLimit;
    }

    public String getUseServerPrepStmts() {
        return useServerPrepStmts;
    }

    public String getUseLocalSessionState() {
        return useLocalSessionState;
    }

    public String getRewriteBatchedStatements() {
        return rewriteBatchedStatements;
    }

    public String getCacheResultSetMetadata() {
        return cacheResultSetMetadata;
    }

    public String getCacheServerConfiguration() {
        return cacheServerConfiguration;
    }

    public String getElideSetAutoCommits() {
        return elideSetAutoCommits;
    }

    public String getMaintainTimeStats() {
        return maintainTimeStats;
    }

    public String getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public String getConnectionTimeout() {
        return connectionTimeout;
    }

    public String getLeakDetectionThreshold() {
        return leakDetectionThreshold;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
