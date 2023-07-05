package nl.tudelft.cornul11.thesis.corpus.jarfile;

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
                String queueSize = "\033[1;31m" + queue.size() + "\033[0m";
                logger.debug("Waiting for file in the queue, current queue size = " + queueSize);
                Path file = queue.take(); // this will block if the queue is empty
                if (POISON_PILL.equals(file)) {
                    // end-of-stream marker encountered
                    break;
                }
                logger.info("Processing file: " + file);
                fileAnalyzer.processJarFile(file);
                logger.info("Finished processing file: " + file);
            }
        } catch (InterruptedException e) {
            logger.error("Thread interrupted while waiting for file in the queue");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Exception while processing file", e);
        }
    }
}
