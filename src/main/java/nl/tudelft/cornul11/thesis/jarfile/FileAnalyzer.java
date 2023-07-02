package nl.tudelft.cornul11.thesis.jarfile;

import net.openhft.hashing.LongHashFunction;
import nl.tudelft.cornul11.thesis.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.file.JarInfoExtractor;
import nl.tudelft.cornul11.thesis.model.Signature;
import nl.tudelft.cornul11.thesis.util.ConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class FileAnalyzer {
    private final List<String> ignoredUberJars = new ArrayList<>();
    private final List<String> insertedLibraries = new ArrayList<>();
    private int insertedUberJars = 0;
    private final SignatureDAO signatureDao;
    private final Logger logger = LoggerFactory.getLogger(FileAnalyzer.class);
    private final Set<Long> uniqueHashes = new HashSet<>();
    private final ConfigurationLoader config;
    private AtomicInteger processedJars = new AtomicInteger(0);
    private int totalJars;
    private long startTime = System.currentTimeMillis();

    public FileAnalyzer(SignatureDAO signatureDao, ConfigurationLoader config) {
        this.config = config;
        this.signatureDao = signatureDao;
        this.totalJars = config.getTotalJars();
    }

    public void printIgnoredUberJars() {
        // output the ignored uber jars to a file
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream("ignored_uber_jars.txt");
            for (String ignoredUberJar : ignoredUberJars) {
                fos.write(ignoredUberJar.getBytes());
                fos.write("\n".getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        try {
            fos = new FileOutputStream("processed_jars.txt");
            for (String jar : insertedLibraries) {
                fos.write(jar.getBytes());
                fos.write("\n".getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        logger.info("Ignored the signatures of " + ignoredUberJars.size() + " uber jars");
        logger.info("Inserted the signatures of " + insertedLibraries.size() + " JARs");
        logger.info("Inserted library information of " + insertedUberJars + " uber JARs");
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

        JarInfoExtractor jarInfoExtractor = new JarInfoExtractor(jarFilePath.toString());
        if (signatures.isEmpty()) { // it's probably an uber-JAR, let's still add it to the db
            insertedUberJars++;
            return commitLibrary(jarInfoExtractor, jarHash, jarCrc, jarHandler.isBrokenJar());
        }

        return commitSignatures(signatures, jarInfoExtractor, jarHash, jarCrc);
    }

    public int commitLibrary(JarInfoExtractor jarInfoExtractor, long jarHash, long jarCrc, boolean isBrokenJar) {
        logger.info("Committing library: " + jarInfoExtractor.getArtifactId() + " version: " + jarInfoExtractor.getVersion());
        return signatureDao.insertLibrary(jarInfoExtractor, jarHash, jarCrc, isBrokenJar);
    }

    public int commitSignatures(List<ClassFileInfo> signatures, JarInfoExtractor jarInfoExtractor, long jarHash, long jarCrc) {
        logJarCommitment(jarInfoExtractor);

        for (ClassFileInfo signature : signatures) {
            uniqueHashes.add(signature.getHashCode());
        }

        List<Signature> signaturesToInsert = getSignaturesToInsert(signatures, jarInfoExtractor);
        int insertedRows = signatureDao.insertSignatures(signaturesToInsert, jarHash, jarCrc);

        if (totalJars > 0) {
            calculateAndLogElapsedTime();
        }
        return insertedRows;
    }

    private void logJarCommitment(JarInfoExtractor jarInfoExtractor) {
        logger.info(String.format("Committing signatures for JAR: %s version: %s",
                jarInfoExtractor.getArtifactId(), jarInfoExtractor.getVersion()));
    }

    private List<Signature> getSignaturesToInsert(List<ClassFileInfo> signatures,
                                                  JarInfoExtractor jarInfoExtractor) {
        return signatures.stream()
                .map(signature -> createSignature(signature, jarInfoExtractor))
                .collect(Collectors.toList());
    }

    private void calculateAndLogElapsedTime() {
        int processed = processedJars.incrementAndGet();
        long elapsedTimeMillis = System.currentTimeMillis() - startTime;
        double elapsedTimeSec = elapsedTimeMillis / 1000.0;
        double timePerJarSec = elapsedTimeSec / processed;

        int remainingJars = totalJars - processed;
        double etaSec = remainingJars * timePerJarSec;

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

    private Signature createSignature(ClassFileInfo signature, JarInfoExtractor jarInfoExtractor) {
        return new Signature(0, signature.getFileName(), signature.getHashCode(), signature.getCrc(), jarInfoExtractor.getGroupId(), jarInfoExtractor.getArtifactId(), jarInfoExtractor.getVersion());
    }
}