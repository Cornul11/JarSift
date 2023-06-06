import nl.tudelft.cornul11.thesis.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.file.JarInfoExtractor;
import nl.tudelft.cornul11.thesis.jarfile.JarSignatureMapper;
import nl.tudelft.cornul11.thesis.jarfile.FileAnalyzer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SignatureEqualTest {
    private static final String JAR_FILE_PATH = "jars/jsr305-2.0.1.jar";

    /**
     * Test that the signatures are equal when using the JarFileProcessor and the JarFileInferenceProcessor
     * In other words, the signatures are equal both when the corpus is being created, and when we extract the signatures
     */
    @Test
    public void testSignatureEqual() {
        SignatureDAO mockDao = mock(SignatureDAO.class);

        FileAnalyzer realProcessor = new FileAnalyzer(mockDao);
        FileAnalyzer processor = spy(realProcessor);
        JarSignatureMapper realInferenceProcessor = new JarSignatureMapper(mockDao);
        JarSignatureMapper inferenceProcessor = spy(realInferenceProcessor);

        Path testPath = getJarPath(JAR_FILE_PATH);

        // process the jar file with both processors
        processor.processJarFile(testPath);
        inferenceProcessor.inferJarFile(testPath);

        // capture the signatures passed to commitSignatures
        ArgumentCaptor<List<ClassFileInfo>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(processor).commitSignatures(argumentCaptor.capture(), any(JarInfoExtractor.class), anyString());

        List<ClassFileInfo> capturedClassFileInfosFromProcessor = argumentCaptor.getValue();

        // capture the method call to checkSignatures
        ArgumentCaptor<List<ClassFileInfo>> checkSignaturesCaptor = ArgumentCaptor.forClass(List.class);
        verify(inferenceProcessor).getTopMatches(checkSignaturesCaptor.capture(), any(SignatureDAO.class));

        List<ClassFileInfo> checkedClassFileInfoFromInference = checkSignaturesCaptor.getValue();

        // assert that both lists have the same size
        assertEquals(capturedClassFileInfosFromProcessor.size(), checkedClassFileInfoFromInference.size());

        // Assert that the signatures are equal
        for (int i = 0; i < capturedClassFileInfosFromProcessor.size(); i++) {
            assertEquals(capturedClassFileInfosFromProcessor.get(i).getFileName(), checkedClassFileInfoFromInference.get(i).getFileName());
            assertEquals(capturedClassFileInfosFromProcessor.get(i).getHashCode(), checkedClassFileInfoFromInference.get(i).getHashCode());
        }
    }

    private Path getJarPath(String jarName) {
        return Paths.get(getClass().getClassLoader().getResource(jarName).getPath());
    }
}