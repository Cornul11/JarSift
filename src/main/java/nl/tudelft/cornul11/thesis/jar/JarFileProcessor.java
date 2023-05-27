package nl.tudelft.cornul11.thesis.jar;

import nl.tudelft.cornul11.thesis.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.database.SignatureDao;
import nl.tudelft.cornul11.thesis.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.file.JarInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JarFileProcessor {
    private final List<String> ignoredUberJars = new ArrayList<>();
    private final SignatureDao signatureDao;
    private final Logger logger = LoggerFactory.getLogger(JarFileProcessor.class);

    public JarFileProcessor(SignatureDao signatureDao) {
        this.signatureDao = signatureDao;
    }

    public void printIgnoredUberJars() {
        for (String ignoredUberJar : ignoredUberJars) {
            System.out.println(ignoredUberJar);
        }
    }

    // TODO: transition to multithreaded operation, process many JARs at once
    public int processJarFile(Path jarFilePath) throws IOException {
        JarFileHandler jarFileHandler = new JarFileHandler(jarFilePath, ignoredUberJars);
        List<ClassFileInfo> classFileInfos = jarFileHandler.extractJarFileInfo();

        // If the classFileInfos is empty, then no need to proceed further.
        if (classFileInfos.isEmpty()) {
            return 0;
        }

        JarInfo jarInfo = new JarInfo(jarFilePath.toString());
        return commitSignatures(classFileInfos, jarInfo);
    }

    public int commitSignatures(List<ClassFileInfo> signatures, JarInfo jarInfo) {
        List<DatabaseManager.Signature> signaturesToInsert = signatures.stream().map(signature -> createSignature(signature, jarInfo)).collect(Collectors.toList());
        return signatureDao.insertSignature(signaturesToInsert);
    }

    private DatabaseManager.Signature createSignature(ClassFileInfo signature, JarInfo jarInfo) {
        return new DatabaseManager.Signature(0, signature.getFileName(), Long.toString(signature.getHashCode()), jarInfo.getGroupId(), jarInfo.getArtifactId(), jarInfo.getVersion());
    }
}