package nl.tudelft.cornul11.thesis.jar;

import nl.tudelft.cornul11.thesis.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;


public class JarInferenceProcessor {
    private final Logger logger = LoggerFactory.getLogger(JarInferenceProcessor.class);
    private final DatabaseManager dbManager;
    private final JarFileInferenceProcessor jarFileInferenceProcessor;

    public JarInferenceProcessor(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.jarFileInferenceProcessor = new JarFileInferenceProcessor();
    }

    public void processJar(String path) {
        Path jarPath = Paths.get(path);
        if (jarPath.toString().endsWith(".jar")) {
            logger.info("Inferring libraries in jar file: " + jarPath);
            jarFileInferenceProcessor.inferJarFile(jarPath, dbManager);
        }
    }
}
