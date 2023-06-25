package nl.tudelft.cornul11.thesis.jarfile;

import nl.tudelft.cornul11.thesis.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeDetails;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeParser;
import nl.tudelft.cornul11.thesis.util.ConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarHandler {
    private static final int MAX_SUBMODULES = 1;
    private static final Set<String> PREFIX_EXCEPTIONS = Set.of("META-INF/", "META-INF/versions/", "test/");
    private static final Set<String> FILENAME_EXCEPTIONS = Set.of("module-info.class", "package-info.class");

    private final Path jarFilePath;
    private final List<String> ignoredUberJars;
    private final List<String> insertedLibraries;
    private final Set<String> mavenSubmodules = new HashSet<>();
    private final Logger logger = LoggerFactory.getLogger(JarHandler.class);
    private final boolean ignoreUberJars;

    public JarHandler(Path jarFilePath, List<String> ignoredUberJars, List<String> insertedLibraries, ConfigurationLoader config) {
        this.jarFilePath = jarFilePath;
        this.ignoredUberJars = ignoredUberJars;
        this.insertedLibraries = insertedLibraries;
        this.ignoreUberJars = config.ignoreUberJars();
    }

    public List<ClassFileInfo> extractJarFileInfo() {
        mavenSubmodules.clear();

        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            String initialClassPrefix = null;
            List<ClassFileInfo> classFileInfos = new ArrayList<>();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (isMavenSubmodule(entry) && shouldSkipDueToSubmoduleCount()) {
                    if (ignoreUberJars) {
                        ignoredUberJars.add(jarFilePath.toString());
                        return new ArrayList<>();
                    }
                }

                if (shouldSkip(entry)) {
                    continue;
                }

                if (isJarFile(entry)) {
                    if (ignoreUberJars) {
                        logger.warn("Found nested JAR file in " + jarFilePath + ", skipping");
                        ignoredUberJars.add(jarFilePath.toString());
                        return new ArrayList<>();
                    }
                }

                if (isClassFile(entry)) {
                    initialClassPrefix = getInitialClassPrefix(entry, initialClassPrefix);
                    if (ignoreUberJars) {
                        if (hasMultiplePackages(jarFilePath, entry, initialClassPrefix)) {
                            ignoredUberJars.add(jarFilePath.toString());
                            return new ArrayList<>();
                        }
                    }

                    ClassFileInfo classFileInfo = processClassFile(entry, jarFile);

                    if (classFileInfo != null) {
                        classFileInfos.add(classFileInfo);
                    }
                }
            }
            insertedLibraries.add(jarFilePath.toString());
            return classFileInfos;
        } catch (FileNotFoundException e) {
            // silenced, this is because of the POISON PILL
            return new ArrayList<>();
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            ignoredUberJars.add(jarFilePath.toString());
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
        return matchesPrefixExceptions(entry) || matchesFilenameExceptions(entry);
    }

    private static boolean matchesPrefixExceptions(JarEntry entry) {
        return PREFIX_EXCEPTIONS.stream()
                .anyMatch(prefix -> entry.getName().startsWith(prefix));
    }

    private static boolean matchesFilenameExceptions(JarEntry entry) {
        return FILENAME_EXCEPTIONS.stream()
                .anyMatch(filename -> entry.getName().contains(filename));
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
            logger.warn("JAR file " + jarFilePath + " contains classes from multiple packages, skipping");
            logger.warn("Initial class prefix: " + initialClassPrefix + ", current class prefix: " + classPrefix);
            return true;
        }
        return false;
    }

    private ClassFileInfo processClassFile(JarEntry entry, JarFile jarFile) throws IOException {
//        logger.debug("Processing class file: " + entry.getName()); // TODO: this makes the logs too verbose
        try (InputStream classFileInputStream = jarFile.getInputStream(entry)) {
            byte[] bytecode = classFileInputStream.readAllBytes();
            BytecodeDetails bytecodeDetails = BytecodeParser.extractSignature(bytecode);
            return new ClassFileInfo(entry.getName(), bytecodeDetails.getSignature());
        }
    }
}