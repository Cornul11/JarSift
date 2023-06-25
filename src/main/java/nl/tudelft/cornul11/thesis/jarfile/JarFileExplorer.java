package nl.tudelft.cornul11.thesis.jarfile;

import nl.tudelft.cornul11.thesis.database.DatabaseWriterThread;
import nl.tudelft.cornul11.thesis.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.database.Task;
import nl.tudelft.cornul11.thesis.file.DirectoryExplorer;
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
    public static final int NUM_CONSUMER_THREADS = 10;
    private final BlockingQueue<Path> queue = new LinkedBlockingQueue<>();
    private final Logger logger = LoggerFactory.getLogger(JarFileExplorer.class);
    private final FileAnalyzer fileAnalyzer;
    // Add a Poison Pill Object to signal end of queue processing
    private static final Path POISON_PILL = Paths.get("");
    private final SignatureDAO signatureDao;
    private final BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>();;

    public JarFileExplorer(SignatureDAO signatureDao) {
        this.signatureDao = signatureDao;
        this.fileAnalyzer = new FileAnalyzer(signatureDao/*, taskQueue*/);
    }

    public void processFiles(String path, String lastPath) {
        Path rootPath = Paths.get(path);
        Path lastVisitedPath = lastPath != null ? Paths.get(lastPath) : null;
        try {
            long startTime = System.currentTimeMillis();
//
//            ExecutorService databaseWriterExecutor = Executors.newSingleThreadExecutor();
//            databaseWriterExecutor.execute(new DatabaseWriterThread(taskQueue));

            // TODO: add tqdm-like progress bar (maybe)


            // create and start JarProcessor threads
            ExecutorService executor = Executors.newFixedThreadPool(NUM_CONSUMER_THREADS);
            for (int i = 0; i < NUM_CONSUMER_THREADS; i++) {
                executor.execute(new JarProcessor(queue, fileAnalyzer));
            }

            logger.info("Processing files in directory: " + rootPath);
            DirectoryExplorer directoryExplorer = new DirectoryExplorer(queue, rootPath);
            directoryExplorer.setLastVisitedPath(lastVisitedPath);
            Files.walkFileTree(rootPath, directoryExplorer);

            // add end-of-stream marker to the queue
            for (int i = 0; i < NUM_CONSUMER_THREADS; i++) { // 10 consumer threads
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

            taskQueue.offer(DatabaseWriterThread.getPoisonTask());
//            databaseWriterExecutor.shutdown();

            long current = System.currentTimeMillis();
            logger.info("Waiting for database writer thread to finish");
//            while (!databaseWriterExecutor.isTerminated()) {
//                try {
//                    databaseWriterExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
//                } catch (InterruptedException e) {
//                    List<Runnable> remainingTasks = databaseWriterExecutor.shutdownNow(); // Force remaining tasks to terminate
//                    logger.error("Interrupted while waiting for tasks to complete", e);
//                    Thread.currentThread().interrupt();
//                }
//            }
//            logger.info("Database writer thread finished in " + (System.currentTimeMillis() - current) / 1000 + " seconds (" + (System.currentTimeMillis() - current) + " ms)");

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
