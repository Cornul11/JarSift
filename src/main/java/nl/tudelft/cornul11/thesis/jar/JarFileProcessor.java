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
import java.util.HashSet;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarFileProcessor {
    private final SignatureDao signatureDao;
    private final Logger logger = LoggerFactory.getLogger(JarFileProcessor.class);
    private static HashSet<String> exceptions;

    static {
        exceptions = new HashSet<>();
        exceptions.add("META-INF/");
        exceptions.add("module-info.class");
        exceptions.add("test/");
    }

    public JarFileProcessor(SignatureDao signatureDao) {
        this.signatureDao = signatureDao;
    }

// TODO: transition to multithreaded operation, process many JARs at once
    public void processJarFile(Path jarFilePath) throws IOException {
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            JarInfo jarInfo = new JarInfo(jarFilePath.toString());

            List<ClassFileInfo> classFileInfos = new ArrayList<>();
            Enumeration<JarEntry> entries = jarFile.entries();
            String initialClassPrefix = null;

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                // define an array containing the K and V, where K and V are both string
                // K is the name of the entry, V is the content of the entry
                if (shouldSkip(entry)) {
                    continue;
                }
                // TODO: jars/org/slf4j/slf4j-api/2.0.4/slf4j-api-2.0.4.jar contains a module-info.class file in
                //  the META-INF folder, and it botches the filtering algorithm

                // TODO: multiple versions of class files for different versions of Java
                // TODO: https://www.logicbig.com/tutorials/core-java-tutorial/java-9-changes/multi-release-jars.html

                // TODO: check plexus-utils-1.5.6.jar, if it's uber or not, and "hidden" is an edge case
                // apparently module-info.class files can also exist in these JARs, so we need to skip them
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    if (initialClassPrefix == null) {
                        // set initialclassPrefix to the substring of the entry name up to the first slash
                        initialClassPrefix = entry.getName().substring(0, entry.getName().indexOf('/') + 1);
                    } else {
                        String classPrefix = entry.getName().substring(0, entry.getName().indexOf('/') + 1);
                        if (!classPrefix.equals(initialClassPrefix)) {
                            logger.warn("JAR file " + jarFilePath + " contains classes from multiple packages, skipping");
                            logger.warn("Initial class prefix: " + initialClassPrefix + ", current class prefix: " + classPrefix);
                            return;
                        }
                    }
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

    private static boolean shouldSkip(JarEntry entry) {
        String name = entry.getName();
        // Skip if the entry is a directory/filename to be ignored
        // module-info.class may also be in a directory, so we need to skip that as well
        if (exceptions.contains(name)) {
            return true;
        }

        // Skip if the entry is in the 'test/' subtree
        if (name.startsWith("test/")) {
            return true;
        }

        return false;
    }

    private void commitSignatures(List<ClassFileInfo> signatures, SignatureDao signatureDao, String groupID, String artifactID, String version) {
        ArrayList<DatabaseManager.Signature> signaturesToInsert = new ArrayList<>();
        for (ClassFileInfo signature : signatures) {
            // what if the hash is already in the database, but its artifacts are different, or the filename was different
            signaturesToInsert.add(new DatabaseManager.Signature(0, signature.getFileName(), Integer.toString(signature.getHashCode()), groupID, artifactID, version));
        }
        signatureDao.insertSignature(signaturesToInsert);
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
