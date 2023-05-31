package nl.tudelft.cornul11.thesis.jarfile;

import nl.tudelft.cornul11.thesis.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.file.JarInfoExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FileAnalyzer {
    private final List<String> ignoredUberJars = new ArrayList<>();
    private final SignatureDAO signatureDao;
    private final Logger logger = LoggerFactory.getLogger(FileAnalyzer.class);

    public FileAnalyzer(SignatureDAO signatureDao) {
        this.signatureDao = signatureDao;
    }

    public void printIgnoredUberJars() {
        for (String ignoredUberJar : ignoredUberJars) {
            System.out.println(ignoredUberJar);
        }
    }

    // TODO: transition to multithreaded operation, process many JARs at once
    public int processJarFile(Path jarFilePath) {
        JarHandler jarHandler = new JarHandler(jarFilePath, ignoredUberJars);
        List<ClassFileInfo> classFileInfos = jarHandler.extractJarFileInfo();

        // If the classFileInfos is empty, then no need to proceed further.
        if (classFileInfos.isEmpty()) {
            return 0;
        }

        JarInfoExtractor jarInfoExtractor = new JarInfoExtractor(jarFilePath.toString());
        return commitSignatures(classFileInfos, jarInfoExtractor);
    }

    public int commitSignatures(List<ClassFileInfo> signatures, JarInfoExtractor jarInfoExtractor) {
        List<DatabaseManager.Signature> signaturesToInsert = signatures.stream().map(signature -> createSignature(signature, jarInfoExtractor)).collect(Collectors.toList());
        return signatureDao.insertSignature(signaturesToInsert);
    }

    private DatabaseManager.Signature createSignature(ClassFileInfo signature, JarInfoExtractor jarInfoExtractor) {
        return new DatabaseManager.Signature(0, signature.getFileName(), Long.toString(signature.getHashCode()), jarInfoExtractor.getGroupId(), jarInfoExtractor.getArtifactId(), jarInfoExtractor.getVersion());
    }
}