package nl.tudelft.cornul11.thesis.corpus.jarfile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class JarFromPathProcessor implements Runnable {
    private final Path path;
    private final FileAnalyzer fileAnalyzer;
    private final Logger logger = LoggerFactory.getLogger(JarFromPathProcessor.class);

    public JarFromPathProcessor(Path path, FileAnalyzer fileAnalyzer) {
        this.path = path;
        this.fileAnalyzer = fileAnalyzer;
    }

    @Override
    public void run() {
        try {
            logger.info("Processing file: " + path);
            fileAnalyzer.processJarFile(path);
            logger.info("Finished processing file: " + path);
        } catch (Exception e) {
            logger.error("Exception while processing file", e);
        }
    }
}
