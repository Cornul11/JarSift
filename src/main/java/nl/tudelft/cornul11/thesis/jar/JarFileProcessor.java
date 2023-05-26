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
    private static final int MAX_SUBMODULES = 3;
    private final SignatureDao signatureDao;
    private final Logger logger = LoggerFactory.getLogger(JarFileProcessor.class);
    private static HashSet<String> exceptions;

    static {
        exceptions = new HashSet<>();
        exceptions.add("META-INF/"); // META-INF should in general not contain any .class files other than in the versions folder
        // but those are, from what I can see, normally from the same package.
        // TODO: we could of course check whether the classpath of the classes located in /versions/ folder under META-INF/
        // are the same as the classpath of the classes in the root of the JAR file for better precision

        // reference: https://www.logicbig.com/tutorials/core-java-tutorial/java-9-changes/multi-release-jars.html
        exceptions.add("META-INF/versions/");
        exceptions.add("module-info.class");
        exceptions.add("hidden/"); // some kind of weird shading, seen in plexus-utils-1.5.6.jar
        exceptions.add("test/");
    }

    public JarFileProcessor(SignatureDao signatureDao) {
        this.signatureDao = signatureDao;
    }

    private boolean isMavenSubmodule(JarEntry entry) {
        return entry.isDirectory() && entry.getName().contains("META-INF/maven/");
    }

    private boolean shouldSkipDueToSubmoduleCount(Path jarFilePath, int mavenSubmoduleCount) {
        if (mavenSubmoduleCount > MAX_SUBMODULES) {
            logger.warn("JAR file " + jarFilePath + " contains more than 3 maven submodules, skipping");
            return true;
        }
        return false;
    }

    private boolean isClassFile(JarEntry entry) {
        return !entry.isDirectory() && entry.getName().endsWith(".class");
    }

    private String getInitialClassPrefix(JarEntry entry, String initialClassPrefix) {
        if (initialClassPrefix == null) {
            initialClassPrefix = getClassPrefix(entry);
        }
        return initialClassPrefix;
    }

    private boolean hasMultiplePackages(Path jarFilePath, JarEntry entry, String initialClassPrefix) {
        String classPrefix = getClassPrefix(entry);
        if (!classPrefix.equals(initialClassPrefix)) {
            logger.warn("JAR file " + jarFilePath + " contains classes from multiple packages, skipping");
            logger.warn("Initial class prefix: " + initialClassPrefix + ", current class prefix: " + classPrefix);
            return true;
        }
        return false;
    }

    private String getClassPrefix(JarEntry entry) {
        return entry.getName().substring(0, entry.getName().indexOf('/') + 1);
    }

    private List<ClassFileInfo> extractJarFileInfo(Path jarFilePath) throws IOException {
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            String initialClassPrefix = null;
            int mavenSubmoduleCount = 0;
            List<ClassFileInfo> classFileInfos = new ArrayList<>();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (isMavenSubmodule(entry)) {
                    mavenSubmoduleCount++;
                    if (shouldSkipDueToSubmoduleCount(jarFilePath, mavenSubmoduleCount)) {
                        return new ArrayList<>();
                    }
                }

                if (shouldSkip(entry)) {
                    continue;
                }

                if (isClassFile(entry)) {
                    initialClassPrefix = getInitialClassPrefix(entry, initialClassPrefix);
                    if (hasMultiplePackages(jarFilePath, entry, initialClassPrefix)) {
                        return new ArrayList<>();
                    }
                    classFileInfos.add(processClassFile(entry, jarFile));
                }
            }
            return classFileInfos;
        }
    }

    // TODO: transition to multithreaded operation, process many JARs at once
    public int processJarFile(Path jarFilePath) throws IOException {
        List<ClassFileInfo> classFileInfos = extractJarFileInfo(jarFilePath);
        JarInfo jarInfo = new JarInfo(jarFilePath.toString());
        return commitSignatures(classFileInfos, jarInfo);
    }

    private static boolean shouldSkip(JarEntry entry) {
        String name = entry.getName();
        // Skip if the entry is a directory/filename to be ignored
        for (String exception : exceptions) {
            if (name.startsWith(exception)) {
                return true;
            }
        }
        return false;
    }

    private int commitSignatures(List<ClassFileInfo> signatures, JarInfo jarInfo) {
        ArrayList<DatabaseManager.Signature> signaturesToInsert = new ArrayList<>();
        for (ClassFileInfo signature : signatures) {
            signaturesToInsert.add(createSignature(signature, jarInfo));
        }
        return signatureDao.insertSignature(signaturesToInsert);
    }

    private DatabaseManager.Signature createSignature(ClassFileInfo signature, JarInfo jarInfo) {
        return new DatabaseManager.Signature(
                0,
                signature.getFileName(),
                Integer.toString(signature.getHashCode()),
                jarInfo.getGroupId(),
                jarInfo.getArtifactId(),
                jarInfo.getVersion());
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
