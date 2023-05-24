package nl.tudelft.cornul11.thesis.jar;

import nl.tudelft.cornul11.thesis.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.database.SignatureDao;
import nl.tudelft.cornul11.thesis.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.file.JarInfo;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeClass;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeSignatureExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarFileProcessor {
    private final Logger logger = LoggerFactory.getLogger(JarFileProcessor.class);
// TODO: transition to multithreaded operation, process many JARs at once
    public void processJarFile(Path jarFilePath, SignatureDao signatureDao) throws IOException {
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            JarInfo jarInfo = new JarInfo(jarFilePath.toString());


            List<ClassFileInfo> classFileInfos = new ArrayList<>();
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    System.out.println("Processing class file: " + entry.getName());
                    classFileInfos.add(processClassFile(entry, jarFile));
                }
//                else {
//                    // TODO: maybe extract version and artifactID from the MANIFEST.MF file?
//                    if (entry.getName().endsWith("pom.xml") && entry.getName().startsWith("META-INF/maven")) {
//                        // only the pom.xml file contained under META-INF/maven in the JAR file is relevant
//                        pomInfo = new PomInfo(new PomProcessor(jarFile.getInputStream(entry), entry.getName()));
//                    }
//                }
            }

//            if (pomInfo == null) {
//                 TODO: maybe still commit signatures without artifactId and version?
//                logger.warn("No pom.xml file found in JAR file: " + jarFilePath);
//                return;
//            }
            commitSignatures(classFileInfos, signatureDao, jarInfo.getGroupId(), jarInfo.getArtifactId(), jarInfo.getVersion());
        }
    }



    private void commitSignatures(List<ClassFileInfo> signatures, SignatureDao signatureDao, String groupID, String artifactID, String version) {
        for (ClassFileInfo signature : signatures) {
            signatureDao.insertSignature(new DatabaseManager.Signature(0, signature.getFileName(), Integer.toString(signature.getHashCode()), groupID, artifactID, version));
            logger.info("Inserted signature into database: " + signature.getFileName());
        }

    }

    private ClassFileInfo processClassFile(JarEntry entry, JarFile jarFile) throws IOException {
        logger.info("Processing class file: " + entry.getName());
        try (InputStream classFileInputStream = jarFile.getInputStream(entry)) {
            byte[] bytecode = classFileInputStream.readAllBytes();
            BytecodeClass bytecodeClass = BytecodeSignatureExtractor.extractSignature(bytecode);
            return new ClassFileInfo(entry.getName(), bytecodeClass.hashCode());
        }
    }
}
