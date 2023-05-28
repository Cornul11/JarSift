import nl.tudelft.cornul11.thesis.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.jarfile.FileAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
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

    private FileAnalyzer fileAnalyzer;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        fileAnalyzer = new FileAnalyzer(signatureDao);
    }

    @Test
    public void testUberJarWithModuleInfoDetection() throws IOException {
        prepareSignatureDaoMock();

        Path jarPath = getJarPath(JAR_WITH_MODULE_INFO_IN_META_INF);
        int totalClassFilesProcessed = fileAnalyzer.processJarFile(jarPath);

        assertNotEquals(0, totalClassFilesProcessed, "All class files should be processed");
    }

    @Test
    public void testUberJarWithManyClasspathDetection() throws IOException {
        prepareSignatureDaoMock();

        Path jarPath = getJarPath(JAR_WITH_MULTIPLE_CLASSPATH);
        int totalClassFilesProcessed = fileAnalyzer.processJarFile(jarPath);

        assertEquals(0, totalClassFilesProcessed, "No class files should be processed");
    }

    @Test
    public void testNormalJarDetection() throws IOException {
        prepareSignatureDaoMock();

        Path jarPath = getJarPath(JAR_WITH_NORMAL_FEATURES);
        int totalClassFilesProcessed = fileAnalyzer.processJarFile(jarPath);

        assertNotEquals(0, totalClassFilesProcessed, "All class files should be processed");
    }

    @Test
    public void testUberJarWithOneClasspathDetection() throws IOException {
        prepareSignatureDaoMock();

        Path jarPath = getJarPath(JAR_WITH_TWO_SUBPROJECTS);
        int totalClassFilesProcessed = fileAnalyzer.processJarFile(jarPath);

        assertEquals(0, totalClassFilesProcessed, "All class files should be processed");
    }


    @Test
    public void testUberJarWithHiddenFolderDetection() throws IOException {
        prepareSignatureDaoMock();

        Path jarPath = getJarPath(JAR_WITH_HIDDEN_FOLDER);
        int totalClassFilesProcessed = fileAnalyzer.processJarFile(jarPath);

        assertEquals(0, totalClassFilesProcessed, "No class files should be processed");
    }

    @Test
    public void testUberJarWithMultipleReleaseDetection() throws IOException {
        prepareSignatureDaoMock();

        Path jarPath = getJarPath(JAR_MULTI_RELEASE_JAR);
        int totalClassFilesProcessed = fileAnalyzer.processJarFile(jarPath);

        assertNotEquals(0, totalClassFilesProcessed, "All class files should be processed");
    }

    @Test
    public void testUberJarWithManyMavenSubprojectsDetection() throws IOException {
        // this JAR file contains only one classpath, but many secondary classpaths at deeper levels
        // which in fact, are many Maven subprojects, thus is an uber-JAR
        prepareSignatureDaoMock();

        Path jarPath = getJarPath(JAR_WITH_MULTIPLE_SUBPROJECTS);
        int totalClassFilesProcessed = fileAnalyzer.processJarFile(jarPath);

        assertEquals(0, totalClassFilesProcessed, "No classes should be processed");
    }

    private void prepareSignatureDaoMock() {
        Mockito.when(signatureDao.insertSignature(Mockito.anyList())).thenAnswer(invocation -> {
            List<DatabaseManager.Signature> signatures = invocation.getArgument(0);
            return signatures.size();
        });
    }

    private Path getJarPath(String jarName) {
        return Paths.get(getClass().getClassLoader().getResource(jarName).getPath());
    }
}