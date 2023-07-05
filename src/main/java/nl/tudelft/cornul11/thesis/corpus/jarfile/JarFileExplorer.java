package nl.tudelft.cornul11.thesis.corpus.jarfile;

import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.file.DirectoryExplorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.*;

public class JarFileExplorer {
    private final int numConsumerThreads;
    private final BlockingQueue<Path> queue = new LinkedBlockingQueue<>();
    private final Logger logger = LoggerFactory.getLogger(JarFileExplorer.class);
    private final FileAnalyzer fileAnalyzer;
    // Add a Poison Pill Object to signal end of queue processing
    private static final Path POISON_PILL = Paths.get("");
    private final SignatureDAO signatureDao;

    public JarFileExplorer(SignatureDAO signatureDao, ConfigurationLoader config) {
        this.signatureDao = signatureDao;
        this.fileAnalyzer = new FileAnalyzer(signatureDao, config);
        this.numConsumerThreads = config.getNumConsumerThreads();
    }

    public void processFiles(String path, String lastPath) {
        Path rootPath = Paths.get(path);
        Path lastVisitedPath = lastPath != null ? Paths.get(lastPath) : null;
        try {
            long startTime = System.currentTimeMillis();

            // TODO: add tqdm-like progress bar (maybe)

            // create and start JarProcessor threads
            logger.info("Starting " + numConsumerThreads + " consumer threads");

            ExecutorService executor = Executors.newFixedThreadPool(numConsumerThreads);
            for (int i = 0; i < numConsumerThreads; i++) {
                executor.execute(new JarProcessor(queue, fileAnalyzer));
            }

            logger.info("Processing files in directory: " + rootPath);
            DirectoryExplorer directoryExplorer = new DirectoryExplorer(queue, rootPath);
            directoryExplorer.setLastVisitedPath(lastVisitedPath);
            Files.walkFileTree(rootPath, directoryExplorer);

            // add end-of-stream marker to the queue
            for (int i = 0; i < numConsumerThreads; i++) { // 10 consumer threads
                queue.offer(POISON_PILL);
            }

            // Wait for all tasks to complete
            executor.shutdown();

            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    List<Runnable> remainingTasks = executor.shutdownNow(); // Force remaining tasks to terminate
                    logger.error("Interrupted while waiting for tasks to complete", e);
                    Thread.currentThread().interrupt();
                }
            }
            long endTime = System.currentTimeMillis();
            logger.info("Processed " + directoryExplorer.getVisitedFilesCount() + " jar file(s) in " + (endTime - startTime) / 1000 + " seconds (" + (endTime - startTime) + " ms)");
            fileAnalyzer.printIgnoredUberJars();
            fileAnalyzer.printStats();
            logger.info("Closing database connection");
            signatureDao.closeConnection();
        } catch (IOException e) {
            logger.error("Error while processing files", e);
        }
    }

    private static boolean isJavaClass(Path file) {
        // 0xCAFEBABE is the magic number for Java class files
        try (DataInputStream input = new DataInputStream(new FileInputStream(file.toFile()))) {
            int magic = input.readInt();
            return magic == 0xCAFEBABE;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
