package nl.tudelft.cornul11.thesis.corpus.jarfile;

import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAOImpl.LibraryCandidate;
import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.BytecodeDetails;
import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.BytecodeParser;
import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.BytecodeUtils;
import nl.tudelft.cornul11.thesis.corpus.file.ClassFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

public class JarSignatureMapper {
    private int totalClassCount = 0;
    private final SignatureDAO signatureDao;
    private static final Logger logger = LoggerFactory.getLogger(JarSignatureMapper.class);
    private static final Set<String> FILENAME_EXCEPTIONS = Set.of("module-info.class", "package-info.class");

    public JarSignatureMapper(SignatureDAO signatureDao) {
        this.signatureDao = signatureDao;
    }

    // Create a thread pool with a fixed number of threads
    int numThreads = Runtime.getRuntime().availableProcessors();

    public List<LibraryCandidate> inferJarFileMultiproccess(Path jarFilePath) {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        totalClassCount = 0;
        logger.info("Proccess the signatures of " + jarFilePath);
        AtomicInteger counter = new AtomicInteger(0);

        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            List<ClassFileInfo> classFileInfos = new ArrayList<>();
            Enumeration<JarEntry> entries = jarFile.entries();

            // Create a list to hold the futures of each class file processing task
            List<Future<List<ClassFileInfo>>> futures = new ArrayList<>();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (isClassFile(entry, entryName)) {
                    Callable<List<ClassFileInfo>> task = () -> processEntry(jarFile.getInputStream(entry), entry);
                    futures.add(executor.submit(task));
                    counter.incrementAndGet();
                } else if (isJarFile(entry, entryName)) {
                    logger.info("Processing nested JAR file: " + entryName);
                    Callable<List<ClassFileInfo>> task = () -> getFileSignatures(jarFile.getInputStream(entry));
                    futures.add(executor.submit(task));
                    counter.incrementAndGet();
                }
            }

            // Wait for all the tasks to complete
            while (counter.get() > 0) {
                for (Iterator<Future<List<ClassFileInfo>>> iterator = futures.iterator(); iterator.hasNext();) {
                    Future<List<ClassFileInfo>> future = iterator.next();
                    if (future.isDone()) {
                        // Retrieve the result from the completed future
                        List<ClassFileInfo> classFileInfo = future.get();
                        iterator.remove();
                        counter.decrementAndGet();

                        if (classFileInfo != null) {
                            classFileInfos.addAll(classFileInfo);
                        }
                    }
                }
            }
            // Shutdown the executor
            executor.shutdown();
            totalClassCount = classFileInfos.size();
            logger.info("Proccessed the signatures of " + classFileInfos.size() + " class files");
            return signatureDao.returnTopLibraryMatches(classFileInfos);
        } catch (IOException | InterruptedException | ExecutionException e) {
            logger.error("Error while processing JAR file: " + jarFilePath, e);
            return null;
        }
    }

    private static boolean isJarFile(JarEntry entry, String entryName) {
        return !entry.isDirectory() && entryName.endsWith(".jar");
    }

    private static boolean isClassFile(JarEntry entry, String entryName) {
        return !entry.isDirectory() && entryName.endsWith(".class")
                && FILENAME_EXCEPTIONS.stream().noneMatch(entryName::contains);
    }

    public List<LibraryCandidate> inferJarFile(InputStream jarInputStream) throws IOException {
        this.totalClassCount = 0;
        List<ClassFileInfo> signatures = getFileSignatures(jarInputStream);
        totalClassCount = signatures.size();
        return signatureDao.returnTopLibraryMatches(signatures);
    }

    public static List<ClassFileInfo> getFileSignatures(InputStream jarInputStream) throws IOException {
        List<ClassFileInfo> classFileInfos = new ArrayList<>();
        try (JarInputStream s = new JarInputStream(jarInputStream)) {
            JarEntry entry;
            while ((entry = s.getNextJarEntry()) != null) {
                classFileInfos.addAll(processEntry(s, entry));
            }
            logger.info("Proccessed the signatures of " + classFileInfos.size() + " class files");
        }
        return classFileInfos;
    }

    private static List<ClassFileInfo> processEntry(InputStream s, JarEntry entry) throws IOException {
        List<ClassFileInfo> classFileInfos = new ArrayList<>();
        String entryName = entry.getName();
        if (isClassFile(entry, entryName)) {
            ClassFileInfo classFileInfo = processClassFile(entry, s);
            if (classFileInfo != null) {
                classFileInfos.add(classFileInfo);
            }
        } else if (isJarFile(entry, entryName)) {
            logger.info("Processing nested JAR file: " + entryName);
            return getFileSignatures(s);
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

        if (matches.size() > 0) {
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

    private static ClassFileInfo processClassFile(JarEntry entry, InputStream classFileInputStream) throws IOException {
        try {
            byte[] bytecode = BytecodeUtils.readBytecodeAndCalculateCRCWhenNotAvailable(entry, classFileInputStream);

            BytecodeDetails bytecodeDetails = BytecodeParser.extractSignature(bytecode);

            return new ClassFileInfo(entry.getName(), BytecodeUtils.getSignatureHash(bytecodeDetails), entry.getCrc());
        } catch (Exception e) {
            logger.error("Error while processing class file: " + entry.getName(), e);
            return null;
        }
    }

    public int getTotalClassCount() {
        return totalClassCount;
    }
}
