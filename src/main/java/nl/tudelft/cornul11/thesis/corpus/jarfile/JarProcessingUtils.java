package nl.tudelft.cornul11.thesis.corpus.jarfile;

import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.BytecodeDetails;
import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.BytecodeParser;
import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.BytecodeUtils;
import nl.tudelft.cornul11.thesis.corpus.file.ClassFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Set;
import java.util.jar.JarEntry;

public class JarProcessingUtils {
    private static final Logger logger = LoggerFactory.getLogger(JarProcessingUtils.class);
    private static final Set<String> PREFIX_EXCEPTIONS = Set.of("META-INF/", "META-INF/versions/", "test/");
    private static final Set<String> FILENAME_EXCEPTIONS = Set.of("module-info.class", "package-info.class");

    private static boolean matchesPrefixExceptions(JarEntry entry) {
        return PREFIX_EXCEPTIONS.stream()
                .anyMatch(prefix -> entry.getName().startsWith(prefix));
    }

    private static boolean matchesFilenameExceptions(JarEntry entry) {
        return FILENAME_EXCEPTIONS.stream()
                .anyMatch(filename -> entry.getName().contains(filename));
    }

    public static boolean shouldSkip(JarEntry entry) {
        return matchesPrefixExceptions(entry) || matchesFilenameExceptions(entry);
    }

    public static boolean isClassFile(JarEntry entry, String entryName) {
        return !entry.isDirectory() && entryName.endsWith(".class");
    }

    public static boolean isJarFile(JarEntry entry, String entryName) {
        return !entry.isDirectory() && entryName.endsWith(".jar");
    }

    public static ClassFileInfo processClassFile(JarEntry entry, InputStream inputStream) {
        try {
            byte[] bytecode = BytecodeUtils.readBytecodeAndCalculateCRCWhenNotAvailable(entry, inputStream);

            BytecodeDetails bytecodeDetails = BytecodeParser.extractSignature(bytecode);
            return new ClassFileInfo(entry.getName(), BytecodeUtils.getSignatureHash(bytecodeDetails), entry.getCrc());
        } catch (Exception e) {
            logger.error("Error while processing class file: " + entry.getName(), e);
            return null;
        }
    }
}
