import nl.tudelft.cornul11.thesis.corpus.database.DatabaseConfig;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.jarfile.FileAnalyzer;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarSignatureMapper;
import nl.tudelft.cornul11.thesis.corpus.model.Signature;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class BytecodeDetailsUberJarDetectionTest {
    private static final String JAR_WITH_MODULE_INFO_IN_META_INF = "jars/slf4j-api-2.0.4.jar";
    private static final String JAR_WITH_HIDDEN_FOLDER = "jars/plexus-utils-1.5.6.jar";
    private static final String JAR_MULTI_RELEASE_JAR = "jars/junit-platform-commons-1.9.3.jar";
    private static final String JAR_WITH_MULTIPLE_SUBPROJECTS = "jars/ikasan-uber-spec-3.2.3.jar";
    private static final String JAR_WITH_TWO_SUBPROJECTS = "jars/maven-shared-utils-0.1.jar";
    private static final String JAR_WITH_MULTIPLE_CLASSPATH = "jars/aspectjweaver-1.9.19.jar";
    private static final String JAR_WITH_NORMAL_FEATURES = "jars/javaparser-core-3.18.0.jar";

    @Mock
    private SignatureDAO signatureDao;

    @Mock
    private ConfigurationLoader config;

    private FileAnalyzer fileAnalyzer;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        Mockito.when(config.ignoreUberJarSignatures()).thenReturn(true);
        fileAnalyzer = new FileAnalyzer(signatureDao, config);
    }

    @Test
    public void testUberJarWithModuleInfoDetection() {
        prepareSignatureDaoMock();

        Path jarPath = getJarPath(JAR_WITH_MODULE_INFO_IN_META_INF);
        int totalClassFilesProcessed = fileAnalyzer.processJarFile(jarPath);

        assertNotEquals(0, totalClassFilesProcessed, "All class files should be processed");
    }

    @Test
    public void testUberJarWithManyClasspathsDetection() {
        prepareSignatureDaoMock();

        Path jarPath = getJarPath(JAR_WITH_MULTIPLE_CLASSPATH);
        int totalClassFilesProcessed = fileAnalyzer.processJarFile(jarPath);

        assertNotEquals(0, totalClassFilesProcessed, "All class files should be processed");
    }

    @Test
    public void testNormalJarDetection() {
        prepareSignatureDaoMock();

        Path jarPath = getJarPath(JAR_WITH_NORMAL_FEATURES);
        int totalClassFilesProcessed = fileAnalyzer.processJarFile(jarPath);

        assertNotEquals(0, totalClassFilesProcessed, "All class files should be processed");
    }

    @Test
    public void testUberJarWithOneClasspathDetection() {
        prepareSignatureDaoMock();

        Path jarPath = getJarPath(JAR_WITH_TWO_SUBPROJECTS);
        int totalClassFilesProcessed = fileAnalyzer.processJarFile(jarPath);

        assertEquals(0, totalClassFilesProcessed, "All class files should be processed");
    }

    // TODO: this needs to be refactored
    @Disabled
    @Test
    public void test() {
        ConfigurationLoader config = new ConfigurationLoader();
        DatabaseConfig databaseConfig = config.getDatabaseConfig();
        DatabaseManager databaseManager = DatabaseManager.getInstance(databaseConfig);
        SignatureDAO signatureDao = databaseManager.getSignatureDao(config.getDatabaseMode());
        JarSignatureMapper jarSignatureMapper = new JarSignatureMapper(signatureDao);
        jarSignatureMapper.inferJarFileMultithreadedProcess(Paths.get("/Users/tdurieux/Downloads/uber-jar-6.5.15.jar"));
    }

    @Test
    public void testUberJarWithHiddenFolderDetection() {
        prepareSignatureDaoMock();

        Path jarPath = getJarPath(JAR_WITH_HIDDEN_FOLDER);
        int totalClassFilesProcessed = fileAnalyzer.processJarFile(jarPath);

        assertEquals(0, totalClassFilesProcessed, "No class files should be processed");
    }

    @Test
    public void testUberJarWithMultipleReleaseDetection() {
        prepareSignatureDaoMock();

        Path jarPath = getJarPath(JAR_MULTI_RELEASE_JAR);
        int totalClassFilesProcessed = fileAnalyzer.processJarFile(jarPath);

        assertNotEquals(0, totalClassFilesProcessed, "All class files should be processed");
    }

    @Test
    public void testUberJarWithManyMavenSubprojectsDetection() {
        // this JAR file contains only one classpath, but many secondary classpaths at
        // deeper levels
        // which in fact, are many Maven subprojects, thus is an uber-JAR
        prepareSignatureDaoMock();

        Path jarPath = getJarPath(JAR_WITH_MULTIPLE_SUBPROJECTS);
        int totalClassFilesProcessed = fileAnalyzer.processJarFile(jarPath);

        assertEquals(0, totalClassFilesProcessed, "No classes should be processed");
    }

    private void prepareSignatureDaoMock() {
        Mockito.when(signatureDao.insertSignatures(Mockito.anyList(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong()))
                .thenAnswer(invocation -> {
                    List<Signature> signatures = invocation.getArgument(0);
                    return signatures.size();
                });
    }

    private Path getJarPath(String jarName) {
        return Paths.get(getClass().getClassLoader().getResource(jarName).getPath());
    }
}