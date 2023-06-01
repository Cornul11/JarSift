package nl.tudelft.cornul11.thesis.jarfile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;


public class JarProcessor implements Runnable {
    private static final Path POISON_PILL = Paths.get("");
    private final BlockingQueue<Path> queue;
    private final FileAnalyzer fileAnalyzer;
    private final Logger logger = LoggerFactory.getLogger(JarProcessor.class);

    public JarProcessor(BlockingQueue<Path> queue, FileAnalyzer fileAnalyzer) {
        this.queue = queue;
        this.fileAnalyzer = fileAnalyzer;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Path file = queue.take(); // this will block if the queue is empty
                if (file == POISON_PILL) {
                    // end-of-stream marker encountered
                    break;
                }
                logger.info("Processing file: " + file);
                fileAnalyzer.processJarFile(file);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}