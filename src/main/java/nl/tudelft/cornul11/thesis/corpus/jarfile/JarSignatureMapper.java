package nl.tudelft.cornul11.thesis.corpus.jarfile;

import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAOImpl.LibraryCandidate;
import nl.tudelft.cornul11.thesis.corpus.file.ClassFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

public class JarSignatureMapper {
    private int totalClassCount = 0;
    private final SignatureDAO signatureDao;
    private static final Logger logger = LoggerFactory.getLogger(JarSignatureMapper.class);

    public JarSignatureMapper(SignatureDAO signatureDao) {
        this.signatureDao = signatureDao;
    }

    // Create a thread pool with a fixed number of threads, which is equal to the number of available processors
    static int numThreads = Runtime.getRuntime().availableProcessors();


    public List<LibraryCandidate> inferJarFileMultithreadedProcess(Path jarFilePath) {
        totalClassCount = 0;
        logger.info("Processing the signatures of " + jarFilePath);

        List<ClassFileInfo> classFileInfos = inferStandaloneJar(jarFilePath);

        if (classFileInfos == null || classFileInfos.isEmpty()) {
            return null;
        }
        totalClassCount = classFileInfos.size();
        logger.info("Processed the signatures of " + classFileInfos.size() + " class files");
        return signatureDao.returnTopLibraryMatches(classFileInfos);
    }

    public static List<ClassFileInfo> inferStandaloneJar(Path jarFilePath) {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Create a list to hold the futures of each class file processing task
        List<Future<List<ClassFileInfo>>> futures = new ArrayList<>();
        List<ClassFileInfo> classFileInfos = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(0);

        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (JarProcessingUtils.shouldSkip(entry)) {
                    continue;
                }

                if (JarProcessingUtils.isClassFile(entry, entryName)) {
                    Callable<List<ClassFileInfo>> task = () -> {
                        try {
                            return processEntry(jarFile.getInputStream(entry), entry);
                        } catch (IOException e) {
                            logger.error("Error while processing entry: " + entry.getName(), e);
                            return Collections.emptyList();
                        }
                    };
                    futures.add(executor.submit(task));
                    counter.incrementAndGet();
                } else if (JarProcessingUtils.isJarFile(entry, entryName)) {
                    logger.info("Processing nested JAR file: " + entryName);
                    Callable<List<ClassFileInfo>> task = () -> {
                        try {
                            return getFileSignatures(jarFile.getInputStream(entry));
                        } catch (IOException | SecurityException e) {
                            logger.error("Error while processing nested JAR file: " + entry.getName(), e);
                            return Collections.emptyList();
                        }
                    };
                    futures.add(executor.submit(task));
                    counter.incrementAndGet();
                }
            }

            // Wait for all the tasks to complete
            while (counter.get() > 0) {
                for (Iterator<Future<List<ClassFileInfo>>> iterator = futures.iterator(); iterator.hasNext(); ) {
                    Future<List<ClassFileInfo>> future = iterator.next();
                    try {
                        if (future.isDone()) {
                            // Retrieve the result from the completed future
                            List<ClassFileInfo> classFileInfo = future.get();
                            iterator.remove();
                            counter.decrementAndGet();

                            if (classFileInfo != null) {
                                classFileInfos.addAll(classFileInfo);
                            }
                        }
                    } catch (ExecutionException e) {
                        logger.error("Error while processing JAR file: " + jarFilePath, e);
                        iterator.remove();
                        counter.decrementAndGet();
                    } catch (InterruptedException e) {
                        logger.error("Error while processing JAR file: " + jarFilePath, e);
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error while processing JAR file: " + jarFilePath, e);
            return null;
        }
        executor.shutdown();
        return classFileInfos;
    }

    public List<LibraryCandidate> inferJarFile(InputStream jarInputStream) throws IOException {
        this.totalClassCount = 0;
        List<ClassFileInfo> signatures = getFileSignatures(jarInputStream);
        totalClassCount = signatures.size();
        return signatureDao.returnTopLibraryMatches(signatures);
    }

    public static List<ClassFileInfo> getFileSignatures(InputStream jarInputStream) {
        List<ClassFileInfo> classFileInfos = new ArrayList<>();
        try (JarInputStream s = new JarInputStream(jarInputStream)) {
            JarEntry entry;
            while ((entry = s.getNextJarEntry()) != null) {
                if (JarProcessingUtils.shouldSkip(entry)) {
                    continue;
                }
                try {
                    classFileInfos.addAll(processEntry(s, entry));
                } catch (IOException | SecurityException e) {
                    logger.error("Error while processing entry: " + entry.getName(), e);
                }
            }
            logger.info("Processed the signatures of " + classFileInfos.size() + " class files");
        } catch (IOException e) {
            logger.error("Error while processing JAR file", e);
        }
        return classFileInfos;
    }

    private static List<ClassFileInfo> processEntry(InputStream jarInputStream, JarEntry entry) throws IOException {
        List<ClassFileInfo> classFileInfos = new ArrayList<>();
        String entryName = entry.getName();
        if (JarProcessingUtils.isClassFile(entry, entryName)) {
            ClassFileInfo classFileInfo = JarProcessingUtils.processClassFile(entry, jarInputStream);
            if (classFileInfo != null) {
                classFileInfos.add(classFileInfo);
            }
        } else if (JarProcessingUtils.isJarFile(entry, entryName)) {
            logger.info("Processing nested JAR file: " + entryName);
            return getFileSignatures(jarInputStream);
        }
        return classFileInfos;
    }

    public List<LibraryCandidate> inferJarFile(Path jarFilePath) {
        try (InputStream jarInputStream = Files.newInputStream(jarFilePath)) {
            return inferJarFile(jarInputStream);
        } catch (IOException e) {
            logger.error("Error while processing JAR file: " + jarFilePath, e);
            return null;
        }
    }

    public static Map<String, Map<String, Object>> getTopMatches(List<LibraryCandidate> matches) {
        Map<String, LibraryCandidate> libraryVersionMap = new HashMap<>();

        if (!matches.isEmpty()) {
            libraryVersionMap = matches.stream()
                    .collect(Collectors.toMap(
                            match -> match.getGroupId() + ":" + match.getArtifactId(), // key
                            match -> match, // value
                            // merge function, in case of key collision, keep the version with maximum count
                            (existing, newOne) -> existing.getHashes().size() > newOne.getHashes().size() ? existing
                                    : newOne));
        }

        // Transform to Map with String keys and Long values
        Map<String, Map<String, Object>> libraryVersionCountMap = libraryVersionMap.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey() + ":" + entry.getValue().getVersion(),
                        entry -> Map.of("count", (long) entry.getValue().getHashes().size(),
                                "total", (long) entry.getValue().getExpectedNumberOfClasses(),
                                "ratio", entry.getValue().getIncludedRatio())));

        return libraryVersionCountMap;
    }

    public int getTotalClassCount() {
        return totalClassCount;
    }
}
