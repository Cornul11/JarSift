package nl.tudelft.cornul11.thesis.oracle;

import nl.tudelft.cornul11.thesis.corpus.database.DatabaseConfig;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;
import org.apache.maven.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PomFilesProcessor {
    private static final Logger logger = LoggerFactory.getLogger(PomFilesProcessor.class);

    private final ExecutorService executor;
    private final Map<String, Model> modelCache;

    public PomFilesProcessor(int threadCount) {
        this.executor = Executors.newFixedThreadPool(threadCount);
        this.modelCache = new ConcurrentHashMap<>();
    }

    public void processPomFiles(Path pathToFile) {
        ConfigurationLoader config = new ConfigurationLoader();
        DatabaseConfig databaseConfig = config.getDatabaseConfig();
        DatabaseManager databaseManager = DatabaseManager.getInstance(databaseConfig);
        SignatureDAO signatureDao = databaseManager.getSignatureDao(config.getDatabaseMode());

        AtomicInteger usingShadePlugin = new AtomicInteger(0);
        AtomicInteger brokenPomCount = new AtomicInteger(0);
        AtomicInteger processedPomCount = new AtomicInteger(0);
        try {
            List<String> allLines;
            long startTime = System.currentTimeMillis();
            long totalLineCount;
            logger.info("Processing files listed in: " + pathToFile);

            try (Stream<String> lines = Files.lines(pathToFile)) {
                allLines = lines.collect(Collectors.toList());
                totalLineCount = allLines.size();
            }

            allLines.stream().map(Paths::get).forEach(path -> {
                executor.submit(new PomProcessor(path, modelCache, usingShadePlugin, brokenPomCount, processedPomCount, signatureDao));
            });

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
            logger.info("Processed all POM files listed in " + pathToFile + " in " + (endTime - startTime) / 1000 + " seconds");

            if (usingShadePlugin.get() == 0) {
                logger.info("No POM files using the shade plugin were found");
            } else {
                logger.info("Total: " + usingShadePlugin.get());
                // print percentage of total
                logger.info("Percentage of total: " + (double) usingShadePlugin.get() / totalLineCount * 100 + "%");
            }
            logger.info("Inserted " + processedPomCount.get() + " POM files into the database");
            logger.warn("Broken POM files: " + brokenPomCount.get());
        } catch (IOException e) {
            logger.error("Error while processing files", e);
        }
    }
}
