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
import java.util.List;
import java.util.stream.Collectors;

public class FileAnalyzer {
    private final List<String> ignoredUberJars = new ArrayList<>();
    private final List<String> insertedLibraries = new ArrayList<>();
    private final SignatureDAO signatureDao;
    private final Logger logger = LoggerFactory.getLogger(FileAnalyzer.class);
    private final List<Long> uniqueHashes = new ArrayList<>();
    private final ConfigurationLoader config;
    public FileAnalyzer(SignatureDAO signatureDao, ConfigurationLoader config) {
        this.config = config;
        this.signatureDao = signatureDao;
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
        logger.info("Actually inserted " + insertedLibraries.size() + " JARs");
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
            return commitLibrary(jarInfoExtractor, jarHash, jarCrc);
        }

        return commitSignatures(signatures, jarInfoExtractor, jarHash, jarCrc);
    }

    public int commitLibrary(JarInfoExtractor jarInfoExtractor, long jarHash, long jarCrc) {
        logger.info("Committing library: " + jarInfoExtractor.getArtifactId() + " version: " + jarInfoExtractor.getVersion());
        return signatureDao.insertLibrary(jarInfoExtractor, jarHash, jarCrc);
    }

    public int commitSignatures(List<ClassFileInfo> signatures, JarInfoExtractor jarInfoExtractor, long jarHash, long jarCrc) {
        logger.info("Committing signatures for JAR: " + jarInfoExtractor.getArtifactId() + " version: " + jarInfoExtractor.getVersion());
        for (ClassFileInfo signature : signatures) {
            if (!uniqueHashes.contains(signature.getHashCode())) {
                uniqueHashes.add(signature.getHashCode());
            }
        }
        List<Signature> signaturesToInsert = signatures.stream().map(signature -> createSignature(signature, jarInfoExtractor)).collect(Collectors.toList());
        return signatureDao.insertSignatures(signaturesToInsert, jarHash, jarCrc);
    }

    public void printStats() {
        // TODO: investigate why the number of unique hashes is not constant for a constant given set of JARs
        logger.info("Total number of unique hashes: " + uniqueHashes.size());
    }

    private Signature createSignature(ClassFileInfo signature, JarInfoExtractor jarInfoExtractor) {
        return new Signature(0, signature.getFileName(), signature.getHashCode(), signature.getCrc(), jarInfoExtractor.getGroupId(), jarInfoExtractor.getArtifactId(), jarInfoExtractor.getVersion());
    }
}