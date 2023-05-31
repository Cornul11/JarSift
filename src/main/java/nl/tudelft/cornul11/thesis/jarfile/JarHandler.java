package nl.tudelft.cornul11.thesis.jarfile;

import nl.tudelft.cornul11.thesis.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeDetails;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarHandler {
    private static final int MAX_SUBMODULES = 1;
    private static final Set<String> EXCEPTIONS = Set.of("META-INF/", "META-INF/versions/", "module-info.class", "test/");

    private final Path jarFilePath;
    private final List<String> ignoredUberJars;
    private final Set<String> mavenSubmodules = new HashSet<>();
    private final Logger logger = LoggerFactory.getLogger(JarHandler.class);

    public JarHandler(Path jarFilePath, List<String> ignoredUberJars) {
        this.jarFilePath = jarFilePath;
        this.ignoredUberJars = ignoredUberJars;
    }

    public List<ClassFileInfo> extractJarFileInfo() {
        mavenSubmodules.clear();

        // /run/media/dan/tmp_Storage/maven-cache/com/github/gwtmaterialdesign/gwt-material/2.0-rc2/gwt-material-2.0-rc2.jar
        // TODO: java.util.zip.ZipException: zip file is empty
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            String initialClassPrefix = null;
            List<ClassFileInfo> classFileInfos = new ArrayList<>();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (isMavenSubmodule(entry) && shouldSkipDueToSubmoduleCount()) {
                    ignoredUberJars.add(jarFilePath.toString());
                    return new ArrayList<>();
                }

                if (shouldSkip(entry)) {
                    continue;
                }

                if (isJarFile(entry)) {
                    logger.warn("Found nested JAR file in " + jarFilePath + ", skipping");
                }

                if (isClassFile(entry)) {
                    initialClassPrefix = getInitialClassPrefix(entry, initialClassPrefix);
                    if (hasMultiplePackages(jarFilePath, entry, initialClassPrefix)) {
                        ignoredUberJars.add(jarFilePath.toString());
                        return new ArrayList<>();
                    }
                    classFileInfos.add(processClassFile(entry, jarFile));
                }
            }
            return classFileInfos;
        } catch (IOException e) {
            logger.error("Error while processing JAR file " + jarFilePath, e);
            return new ArrayList<>();
        }
    }

    private boolean isMavenSubmodule(JarEntry entry) {
        if (entry.isDirectory() && entry.getName().startsWith("META-INF/maven/")) {
            String[] parts = entry.getName().split("/");
            if (parts.length >= 4) { // length must be at least 4 to include a group ID and artifact ID
                String submodule = parts[2] + "/" + parts[3];
                return mavenSubmodules.add(submodule);
            }
        }
        return false;
    }

    private boolean shouldSkipDueToSubmoduleCount() {
        if (mavenSubmodules.size() > MAX_SUBMODULES) {
            logger.warn("JAR file " + jarFilePath + " contains more than " + MAX_SUBMODULES + " maven submodules, skipping");
            return true;
        }
        return false;
    }

    private static boolean shouldSkip(JarEntry entry) {
        return EXCEPTIONS.stream().anyMatch(entry.getName()::startsWith);
    }

    private boolean isJarFile(JarEntry entry) {
        return !entry.isDirectory() && entry.getName().endsWith(".jar");
    }

    private boolean isClassFile(JarEntry entry) {
        return !entry.isDirectory() && entry.getName().endsWith(".class");
    }

    private String getClassPrefix(JarEntry entry) {
        return entry.getName().substring(0, entry.getName().indexOf('/') + 1);
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
            // TODO: junit has more than one package in later version, and gets flagged as an uber-JAR
            // it has folders ${JAR_root}/org/junit and ${JAR_root}/junit
            logger.warn("JAR file " + jarFilePath + " contains classes from multiple packages, skipping");
            logger.warn("Initial class prefix: " + initialClassPrefix + ", current class prefix: " + classPrefix);
            return true;
        }
        return false;
    }

    private ClassFileInfo processClassFile(JarEntry entry, JarFile jarFile) throws IOException {
        // TODO: make it a bit less verbose for now
//        logger.info("Processing class file: " + entry.getName());
        try (InputStream classFileInputStream = jarFile.getInputStream(entry)) {
            byte[] bytecode = classFileInputStream.readAllBytes();
            BytecodeDetails bytecodeDetails = BytecodeParser.extractSignature(bytecode);
            return new ClassFileInfo(entry.getName(), bytecodeDetails.getSignature());
        }
    }
}