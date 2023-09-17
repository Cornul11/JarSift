import nl.tudelft.cornul11.thesis.corpus.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarHandler;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarSignatureMapper;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class SignatureEqualTest {
    private static final String JAR_FILE_PATH = "jars/jsr305-2.0.1.jar";

    /**
     * Test that the signatures are equal when using the JarSignatureMapper and the JarHandler
     * In other words, the signatures are equal both when the corpus is being created, and when we extract the signatures
     */
    @Test
    public void testSignatureEqual() {
        Path jarFilePath = getJarPath();
        List<ClassFileInfo> signatureMapperSignatures = JarSignatureMapper.inferStandaloneJar(jarFilePath);

        JarHandler jarHandler = new JarHandler(jarFilePath,
                new ConcurrentLinkedDeque<>(),
                new ConcurrentLinkedDeque<>(),
                new ConfigurationLoader());
        List<ClassFileInfo> handlerSignatures = jarHandler.extractSignatures();

        // assert that both lists have the same size
        assertNotEquals(signatureMapperSignatures, null);
        assertEquals(handlerSignatures.size(), signatureMapperSignatures.size());

        // sort handlerSignatures and signatureMapperSignatures based on the class name
        handlerSignatures.sort(Comparator.comparing(ClassFileInfo::getClassName));
        signatureMapperSignatures.sort(Comparator.comparing(ClassFileInfo::getClassName));

        // Assert that the signatures are equal
        for (int i = 0; i < handlerSignatures.size(); i++) {
            assertEquals(handlerSignatures.get(i).getClassName(), signatureMapperSignatures.get(i).getClassName());
            assertEquals(handlerSignatures.get(i).getHashCode(), signatureMapperSignatures.get(i).getHashCode());
        }
    }

    private Path getJarPath() {
        return Paths.get(getClass().getClassLoader().getResource(SignatureEqualTest.JAR_FILE_PATH).getPath());
    }
}