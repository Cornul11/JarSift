package nl.tudelft.cornul11.thesis.jar;

import nl.tudelft.cornul11.thesis.database.SignatureDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class JarInferenceProcessor {
    private final Logger logger = LoggerFactory.getLogger(JarInferenceProcessor.class);
    private final JarFileInferenceProcessor jarFileInferenceProcessor;

    public JarInferenceProcessor(SignatureDao signatureDao) {
        this.jarFileInferenceProcessor = new JarFileInferenceProcessor(signatureDao);
    }

    public Map<String, Long> processJar(String path) {
        Map<String, Long> frequencyMap = null;
        Path jarPath = Paths.get(path);
        if (jarPath.toString().endsWith(".jar")) {
            logger.info("Inferring libraries in jar file: " + jarPath);
            frequencyMap = jarFileInferenceProcessor.inferJarFile(jarPath);
        }
        return frequencyMap;
    }
}
