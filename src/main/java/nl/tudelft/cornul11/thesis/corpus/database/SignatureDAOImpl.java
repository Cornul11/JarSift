package nl.tudelft.cornul11.thesis.corpus.database;

import com.zaxxer.hikari.HikariDataSource;
import nl.tudelft.cornul11.thesis.corpus.file.JarAndPomInfoExtractor;
import nl.tudelft.cornul11.thesis.corpus.file.LibraryMatchInfo;
import nl.tudelft.cornul11.thesis.corpus.model.Signature;
import nl.tudelft.cornul11.thesis.oracle.PomProcessor;
import org.apache.maven.model.*;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
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
    public int insertLibrary(JarAndPomInfoExtractor jarAndPomInfoExtractor, long jarHash, long jarCrc, boolean isBrokenJar) {
        String insertLibraryQuery = "INSERT INTO libraries (group_id, artifact_id, version, jar_hash, jar_crc, is_uber_jar, total_class_files) VALUES (?, ?, ?, ?, ?, ?, ?)";

        executeWithDeadlockRetry(connection -> {
            try (PreparedStatement libraryStatement = connection.prepareStatement(insertLibraryQuery, Statement.RETURN_GENERATED_KEYS)) {
                libraryStatement.setString(1, jarAndPomInfoExtractor.getGroupId());
                libraryStatement.setString(2, jarAndPomInfoExtractor.getArtifactId());
                libraryStatement.setString(3, jarAndPomInfoExtractor.getVersion());
                libraryStatement.setLong(4, jarHash);
                libraryStatement.setLong(5, jarCrc);
                libraryStatement.setBoolean(6, !isBrokenJar);
                // there won't be any matches with this lib because there is no signature in the db, thus we don't need the total number of class files in it
                libraryStatement.setInt(7, -1);
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
        String insertLibraryQuery = "INSERT INTO libraries (group_id, artifact_id, version, jar_hash, jar_crc, is_uber_jar, total_class_files) VALUES (?, ?, ?, ?, ?, ?, ?)";
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
                libraryStatement.setInt(7, signatures.size());
                libraryStatement.executeUpdate();

                ResultSet generatedKeys = libraryStatement.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int libraryId = generatedKeys.getInt(1);

                    PreparedStatement insertStatement = connection.prepareStatement(insertSignatureQuery);

                    // TODO: maybe switch to batch inserts here

                    int i = 0;
                    for (Signature signature : signatures) {
                        insertStatement.setInt(1, libraryId);  // setting the library_id for each signature
                        insertStatement.setLong(2, signature.getHash());
                        insertStatement.setLong(3, signature.getCrc());
                        insertStatement.addBatch();

                        if (++i % 1000 == 0 || i == signatures.size()) {
                            int[] updatedRows = insertStatement.executeBatch();
                            for (int updatedRow : updatedRows) {
                                totalRowsInserted.addAndGet(updatedRow);
                            }
                        }

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
    public Iterator<String> getAllPossibleLibraries() {
        return new LibraryIterator(ds);
    }

    public class OracleLibrary {
        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }

        public boolean isAnUberJar() {
            return isAnUberJar;
        }

        private final String groupId;
        private final String artifactId;
        private final String version;
        private final boolean isAnUberJar;

        public OracleLibrary(String groupId, String artifactId, String version, boolean isAnUberJar) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.isAnUberJar = isAnUberJar;
        }
    }

    @Override
    public List<OracleLibrary> getOracleLibraries() {
        String selectLibrariesQuery = "SELECT group_id, artifact_id, version, is_an_uber_jar FROM oracle_libraries";

        List<OracleLibrary> libraries = new ArrayList<>();
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectLibrariesQuery, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            statement.setFetchSize(1000);  // Fetch 1000 rows at a time
            statement.setFetchDirection(ResultSet.FETCH_FORWARD);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    libraries.add(new OracleLibrary(resultSet.getString("group_id"), resultSet.getString("artifact_id"), resultSet.getString("version"), resultSet.getBoolean("is_an_uber_jar")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return libraries;
    }

    @Override
    public boolean isLibraryInDB(String library) {
        String selectLibraryQuery = "SELECT 1 FROM libraries WHERE CONCAT(group_id, ':', artifact_id, ':', version) = ?";
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectLibraryQuery)) {
            statement.setString(1, library);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<LibraryMatchInfo> returnTopLibraryMatches(List<Long> hashes) {
        long startTime = System.currentTimeMillis();

        String createTempTable = "CREATE TEMPORARY TABLE IF NOT EXISTS temp_hashes (class_hash BIGINT NOT NULL)";
        String dropTempTable = "DROP TABLE temp_hashes";
        String insertIntoTempTable = "INSERT INTO temp_hashes (class_hash) VALUES (?)";

        String mainQuery = "SELECT library_id, group_id, artifact_id, version, total_class_files, COUNT(*) as matched_count " +
                "FROM signatures " +
                "JOIN libraries ON signatures.library_id = libraries.id " +
                "JOIN temp_hashes ON signatures.class_hash = temp_hashes.class_hash " +
                "GROUP BY library_id";

        List<LibraryMatchInfo> libraryHashesCount = new ArrayList<>();

        try (Connection connection = ds.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(createTempTable);
            }

            try (PreparedStatement statement = connection.prepareStatement(insertIntoTempTable)) {
                for (Long hash : hashes) {
                    statement.setLong(1, hash);
                    statement.addBatch();
                }
                statement.executeBatch();
            }

            try (PreparedStatement statement = connection.prepareStatement(mainQuery)) {
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    String resultGroupId = resultSet.getString("group_id");
                    String resultArtifactId = resultSet.getString("artifact_id");
                    String resultVersion = resultSet.getString("version");
                    int resultMatchedCount = resultSet.getInt("matched_count");
                    int resultTotalCount = resultSet.getInt("total_class_files");

                    LibraryMatchInfo libraryMatchInfo = new LibraryMatchInfo(resultGroupId, resultArtifactId, resultVersion, resultMatchedCount, resultTotalCount);
                    libraryHashesCount.add(libraryMatchInfo);
                }
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute(dropTempTable);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();
        logger.info("Top matches query took " + (endTime - startTime) / 1000.0 + " seconds.");

        return libraryHashesCount;
    }

    @Override
    public void insertPluginInfo(Model model, Plugin shadePlugin, boolean minimizeJar, boolean usingMavenShade, boolean isUberJar) {
        long startTime = System.currentTimeMillis();

        String insertLibraryQuery = "INSERT INTO oracle_libraries (group_id, artifact_id, version, using_maven_shade_plugin, is_an_uber_jar) VALUES (?, ?, ?, ?, ?)";
        String insertDependencyQuery = "INSERT INTO dependencies (library_id, group_id, artifact_id, version, scope) VALUES (?, ?, ?, ?, ?)";
        String insertPluginQuery = "INSERT INTO plugins (library_id, group_id, artifact_id, version) VALUES (?, ?, ?, ?)";
        String insertPluginConfigQuery = "INSERT INTO plugin_config (plugin_id, execution_id, config, using_minimize_jar) VALUES (?, ?, ?, ?)";

        executeWithDeadlockRetry(connection -> {
            try {
                PreparedStatement libraryStatement = connection.prepareStatement(insertLibraryQuery, Statement.RETURN_GENERATED_KEYS);
                libraryStatement.setString(1, model.getGroupId());
                libraryStatement.setString(2, model.getArtifactId());
                libraryStatement.setString(3, model.getVersion());
                libraryStatement.setBoolean(4, usingMavenShade);
                libraryStatement.setBoolean(5, isUberJar);
                libraryStatement.executeUpdate();

                ResultSet generatedKeys = libraryStatement.getGeneratedKeys();

                if (generatedKeys.next()) {
                    int libraryId = generatedKeys.getInt(1);

                    List<Dependency> dependencies = model.getDependencies();
                    PreparedStatement dependencyStatement = connection.prepareStatement(insertDependencyQuery);
                    for (Dependency dependency : dependencies) {
                        dependencyStatement.setInt(1, libraryId);
                        dependencyStatement.setString(2, dependency.getGroupId());
                        dependencyStatement.setString(3, dependency.getArtifactId());
                        dependencyStatement.setString(4, dependency.getVersion());
                        dependencyStatement.setString(5, dependency.getScope());
                        dependencyStatement.executeUpdate();
                    }

                    if (shadePlugin != null) {
                        PreparedStatement pluginStatement = connection.prepareStatement(insertPluginQuery, Statement.RETURN_GENERATED_KEYS);
                        pluginStatement.setInt(1, libraryId);
                        pluginStatement.setString(2, shadePlugin.getGroupId());
                        pluginStatement.setString(3, shadePlugin.getArtifactId());
                        pluginStatement.setString(4, shadePlugin.getVersion());
                        pluginStatement.executeUpdate();

                        generatedKeys = pluginStatement.getGeneratedKeys();
                        if (generatedKeys.next()) {
                            int pluginId = generatedKeys.getInt(1);


                            // save plugin-level configuration
                            Object pluginConfiguration = shadePlugin.getConfiguration();
                            if (pluginConfiguration != null) {
                                try {
                                    String serializedConfiguration = PomProcessor.serializeXpp3Dom((Xpp3Dom) pluginConfiguration);
                                    PreparedStatement configStatement = connection.prepareStatement(insertPluginConfigQuery);
                                    configStatement.setInt(1, pluginId);
                                    configStatement.setString(2, null);
                                    configStatement.setString(3, serializedConfiguration);
                                    configStatement.setBoolean(4, minimizeJar);
                                    configStatement.executeUpdate();
                                } catch (Exception e) {
                                    logger.debug("The error occurred during the serialization of the plugin configuration.", e);
                                }
                            } else {
                                logger.debug("The plugin configuration is null.");
                            }

                            // save execution-level configuration
                            List<PluginExecution> executions = shadePlugin.getExecutions();
                            for (PluginExecution execution : executions) {
                                Object configuration = execution.getConfiguration();
                                if (configuration != null) {
                                    try {
                                        String serializedConfiguration = PomProcessor.serializeXpp3Dom((Xpp3Dom) configuration);
                                        PreparedStatement configStatement = connection.prepareStatement(insertPluginConfigQuery);
                                        configStatement.setInt(1, pluginId);
                                        configStatement.setString(2, execution.getId());
                                        configStatement.setString(3, serializedConfiguration);
                                        configStatement.setBoolean(4, minimizeJar);
                                        configStatement.executeUpdate();
                                    } catch (Exception e) {
                                        logger.debug("The error occurred during the serialization of the plugin configuration.", e);
                                    }
                                } else {
                                    logger.debug("The plugin execution configuration is null.");
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });

        long endTime = System.currentTimeMillis();
        logger.info("Inserting plugin info took " + (endTime - startTime) / 1000.0 + " seconds.");
    }

    @Override
    public Model retrievePluginInfo(String groupId, String artifactId, String version) {
        String selectLibraryQuery = "SELECT * FROM oracle_libraries WHERE group_id = ? AND artifact_id = ? AND version = ?";
        String selectDependencyQuery = "SELECT * FROM dependencies WHERE library_id = ?";
        String selectPluginQuery = "SELECT * FROM plugins WHERE library_id = ?";
        String selectPluginConfigQuery = "SELECT * FROM plugin_config WHERE plugin_id = ? ORDER BY execution_id";

        Model model = new Model();

        executeWithDeadlockRetry(connection -> {
            try {
                PreparedStatement libraryStatement = connection.prepareStatement(selectLibraryQuery);
                libraryStatement.setString(1, groupId);
                libraryStatement.setString(2, artifactId);
                libraryStatement.setString(3, version);

                ResultSet libraryResultSet = libraryStatement.executeQuery();
                if (libraryResultSet.next()) {
                    model.setGroupId(libraryResultSet.getString("group_id"));
                    model.setArtifactId(libraryResultSet.getString("artifact_id"));
                    model.setVersion(libraryResultSet.getString("version"));

                    int libraryId = libraryResultSet.getInt("id");

                    PreparedStatement dependencyStatement = connection.prepareStatement(selectDependencyQuery);
                    dependencyStatement.setInt(1, libraryId);

                    ResultSet dependencyResultSet = dependencyStatement.executeQuery();
                    List<Dependency> dependencies = new ArrayList<>();
                    while (dependencyResultSet.next()) {
                        Dependency dependency = new Dependency();
                        dependency.setGroupId(dependencyResultSet.getString("group_id"));
                        dependency.setArtifactId(dependencyResultSet.getString("artifact_id"));
                        dependency.setVersion(dependencyResultSet.getString("version"));
                        dependency.setScope(dependencyResultSet.getString("scope"));
                        dependencies.add(dependency);
                    }
                    model.setDependencies(dependencies);

                    PreparedStatement pluginStatement = connection.prepareStatement(selectPluginQuery);
                    pluginStatement.setInt(1, libraryId);

                    ResultSet pluginResultSet = pluginStatement.executeQuery();
                    Build build = model.getBuild();
                    if (build == null) {
                        build = new Build();
                    }

                    while (pluginResultSet.next()) {
                        Plugin plugin = new Plugin();
                        plugin.setGroupId(pluginResultSet.getString("group_id"));
                        plugin.setArtifactId(pluginResultSet.getString("artifact_id"));
                        plugin.setVersion(pluginResultSet.getString("version"));

                        int pluginId = pluginResultSet.getInt("id");

                        PreparedStatement configStatement = connection.prepareStatement(selectPluginConfigQuery);
                        configStatement.setInt(1, pluginId);
                        ResultSet configResultSet = configStatement.executeQuery();

                        // handle the first configuration as plugin level configuration (because we add it first)
                        // and the rest as execution level configurations
                        boolean isFirstConfig = true;
                        List<PluginExecution> executions = new ArrayList<>();
                        while (configResultSet.next()) {
                            Xpp3Dom config = Xpp3DomBuilder.build(new StringReader(configResultSet.getString("config")));
                            if (isFirstConfig) {
                                plugin.setConfiguration(config);
                                isFirstConfig = false;
                            } else {
                                PluginExecution execution = new PluginExecution();
                                execution.setConfiguration(config);
                                executions.add(execution);
                            }
                        }

                        plugin.setExecutions(executions);

                        build.addPlugin(plugin);
                    }
                    model.setBuild(build);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });

        return model;
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