package nl.tudelft.cornul11.thesis.corpus.jarfile;

import nl.tudelft.cornul11.thesis.corpus.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.CRC32;


public class JarHandler {
    private static final int MAX_SUBMODULES = 1;

    private final Path jarFilePath;
    private final ConcurrentLinkedDeque<String> ignoredUberJars;
    private final ConcurrentLinkedDeque<String> insertedLibraries;
    private final Set<String> mavenSubmodules = new HashSet<>();
    private final Logger logger = LoggerFactory.getLogger(JarHandler.class);
    private final boolean ignoreUberJarSignatures;
    private final CRC32 jarCrc = new CRC32();
    private long jarCreationDate = -1;
    private final long crcValue;
    private boolean brokenJar = false;

    public JarHandler(Path jarFilePath, ConcurrentLinkedDeque<String> ignoredUberJars, ConcurrentLinkedDeque<String> insertedLibraries, ConfigurationLoader config) {
        this.jarFilePath = jarFilePath;
        this.ignoredUberJars = ignoredUberJars;
        this.insertedLibraries = insertedLibraries;
        this.ignoreUberJarSignatures = config.ignoreUberJarSignatures();
        this.crcValue = this.generateCrc();
    }

    public boolean isBrokenJar() {
        return brokenJar;
    }

    private long generateCrc() {
        jarCrc.reset();

        try (FileChannel channel = FileChannel.open(jarFilePath)) {
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            byte[] bytes = new byte[8192];

            while (buffer.hasRemaining()) {
                int length = Math.min(buffer.remaining(), bytes.length);
                buffer.get(bytes, 0, length);
                jarCrc.update(bytes, 0, length);
            }
        } catch (IOException e) {
            logger.error("Failed to generate CRC for " + jarFilePath, e);
        }

        return jarCrc.getValue();
    }

    public List<ClassFileInfo> extractSignatures() {
        mavenSubmodules.clear();

        logger.info("Attempting to process " + jarFilePath);
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            List<ClassFileInfo> classFileInfos = new ArrayList<>();
            logger.info("Processing " + jarFilePath + " with " + jarFile.size() + " entries");
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (entryName.equals("META-INF/MANIFEST.MF")) {
                    jarCreationDate = entry.getTime();
                }

                if (isMavenSubmodule(entry) && shouldSkipDueToSubmoduleCount() && jarCreationDate != -1) {
                    if (ignoreUberJarSignatures) {
                        ignoredUberJars.add(jarFilePath.toString());
                        return new ArrayList<>();
                    }
                }

                if (JarProcessingUtils.shouldSkip(entry)) {
                    continue;
                }

                if (JarProcessingUtils.isJarFile(entry, entryName)) {
                    if (ignoreUberJarSignatures) {
                        logger.warn("Found nested JAR file in " + jarFilePath + ", skipping");
                        ignoredUberJars.add(jarFilePath.toString());
                        return new ArrayList<>();
                    }
                }

                if (JarProcessingUtils.isClassFile(entry, entryName)) {
                    ClassFileInfo classFileInfo = JarProcessingUtils.processClassFile(entry, jarFile.getInputStream(entry));

                    if (classFileInfo != null) {
                        classFileInfos.add(classFileInfo);
                    }
                }
            }
            logger.info("Finished processing " + jarFilePath);
            insertedLibraries.add(jarFilePath.toString());
            return classFileInfos;
        } catch (FileNotFoundException e) {
            // silenced, this is because of the POISON PILL
            brokenJar = true;
            return new ArrayList<>();
        } catch (Exception e) { // goddamn broken JARs
            ignoredUberJars.add(jarFilePath.toString());
            brokenJar = true;
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

    public long getJarCrc() {
        return crcValue;
    }

    public long getJarCreationDate() {
        return jarCreationDate;
    }
}