package nl.tudelft.cornul11.thesis.corpus.jarfile;

import net.openhft.hashing.LongHashFunction;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.corpus.file.JarAndPomInfoExtractor;
import nl.tudelft.cornul11.thesis.corpus.model.Signature;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class FileAnalyzer {
    private final ConcurrentLinkedDeque<String> ignoredUberJars = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<String> insertedLibraries = new ConcurrentLinkedDeque<>();
    private final AtomicInteger insertedUberJars = new AtomicInteger(0);
    private final SignatureDAO signatureDao;
    private final Logger logger = LoggerFactory.getLogger(FileAnalyzer.class);
    private final ConcurrentHashMap<Long, Boolean> uniqueHashes = new ConcurrentHashMap<>();
    private final ConfigurationLoader config;
    private final AtomicInteger processedJars = new AtomicInteger(0);
    private final int totalJars;
    private final long startTime = System.currentTimeMillis();

    public FileAnalyzer(SignatureDAO signatureDao, ConfigurationLoader config) {
        this.config = config;
        this.signatureDao = signatureDao;
        this.totalJars = config.getTotalJars();
    }

    public void printIgnoredUberJars() {
        logger.info("Ignored the signatures of " + ignoredUberJars.size() + " uber jars");
        logger.info("Inserted the signatures of " + insertedLibraries.size() + " JARs");
        logger.info("Inserted library information of " + insertedUberJars + " uber JARs");
    }

    public void setProcessedJars(int processedJars) {
        this.processedJars.set(processedJars);
    }

    public int processJarFile(Path jarFilePath) {
        JarHandler jarHandler = new JarHandler(jarFilePath, ignoredUberJars, insertedLibraries, config);
        List<ClassFileInfo> signatures = jarHandler.extractSignatures();

        StringBuilder sb = new StringBuilder();
        for (ClassFileInfo signature : signatures) {
            sb.append(signature.getHashCode());
        }
        LongHashFunction xx = LongHashFunction.xx();
        long jarHash = xx.hashChars(sb.toString());
        long jarCrc = jarHandler.getJarCrc();
        long jarCreationDate = jarHandler.getJarCreationDate();

        JarAndPomInfoExtractor jarAndPomInfoExtractor = new JarAndPomInfoExtractor(jarFilePath.toString());
        if (signatures.isEmpty()) { // it's probably an uber-JAR, let's still add it to the db
            insertedUberJars.incrementAndGet();
            return commitLibrary(jarAndPomInfoExtractor, jarHash, jarCrc, jarHandler.isBrokenJar(), jarCreationDate);
        }

        // TODO: maybe switch to a thread that does the insertions, and many other threads do the extraction and processing
        return commitSignatures(signatures, jarAndPomInfoExtractor, jarHash, jarCrc, jarCreationDate);
    }

    public int commitLibrary(JarAndPomInfoExtractor jarAndPomInfoExtractor, long jarHash, long jarCrc, boolean isBrokenJar, long jarCreationDate) {
        logger.info("Committing library: " + jarAndPomInfoExtractor.getArtifactId() + " version: " + jarAndPomInfoExtractor.getVersion());
        return signatureDao.insertLibrary(jarAndPomInfoExtractor, jarHash, jarCrc, isBrokenJar, jarCreationDate);
    }

    public int commitSignatures(List<ClassFileInfo> signatures, JarAndPomInfoExtractor jarAndPomInfoExtractor, long jarHash, long jarCrc, long jarCreationDate) {
        logJarCommitment(jarAndPomInfoExtractor);

        for (ClassFileInfo signature : signatures) {
            uniqueHashes.put(signature.getHashCode(), true);
        }

        List<Signature> signaturesToInsert = getSignaturesToInsert(signatures, jarAndPomInfoExtractor);
        int insertedRows = signatureDao.insertSignatures(signaturesToInsert, jarHash, jarCrc, jarCreationDate);

        if (totalJars > 0) {
            calculateAndLogElapsedTime();
        }
        return insertedRows;
    }

    private void logJarCommitment(JarAndPomInfoExtractor jarAndPomInfoExtractor) {
        logger.info(String.format("Committing signatures for JAR: %s version: %s",
                jarAndPomInfoExtractor.getArtifactId(), jarAndPomInfoExtractor.getVersion()));
    }

    private List<Signature> getSignaturesToInsert(List<ClassFileInfo> signatures,
                                                  JarAndPomInfoExtractor jarAndPomInfoExtractor) {
        return signatures.stream()
                .map(signature -> createSignature(signature, jarAndPomInfoExtractor))
                .collect(Collectors.toList());
    }

    private void calculateAndLogElapsedTime() {
        int processed = processedJars.incrementAndGet();
        long currentTime = System.currentTimeMillis();
        long elapsedTimeMillis = currentTime - startTime;
        double elapsedTimeSec = elapsedTimeMillis / 1000.0;
        double timePerJarSec = processed > 0 ? elapsedTimeSec / processed : 0;

        int remainingJars = totalJars - processed;
        double etaSec = remainingJars > 0 ? remainingJars * timePerJarSec : 0;

        int etaMin = (int) (etaSec / 60);
        int etaSecs = (int) (etaSec % 60);
        int etaHour = etaMin / 60;
        int etaMins = etaMin % 60;
        int etaDays = etaHour / 24;
        int etaHours = etaHour % 24;

        logger.info(String.format("Done processing %d/%d JARs, progress: \u001B[94m%d%%\u001B[0m, ETA: %d days, %d hours, %d minutes and %d seconds",
                processed, totalJars, (processed * 100 / totalJars), etaDays, etaHours, etaMins, etaSecs));

    }
    public void printStats() {
        // TODO: investigate why the number of unique hashes is not constant for a constant given set of JARs
        logger.info("Total number of unique hashes: " + uniqueHashes.size());
    }

    private Signature createSignature(ClassFileInfo signature, JarAndPomInfoExtractor jarAndPomInfoExtractor) {
        return new Signature(0, signature.getClassName(), signature.getHashCode(), signature.getCrc(), jarAndPomInfoExtractor.getGroupId(), jarAndPomInfoExtractor.getArtifactId(), jarAndPomInfoExtractor.getVersion());
    }

    public int getProcessedFiles() {
        return insertedLibraries.size();
    }
}