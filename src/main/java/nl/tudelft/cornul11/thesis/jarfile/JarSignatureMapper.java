package nl.tudelft.cornul11.thesis.jarfile;

import nl.tudelft.cornul11.thesis.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.file.LibraryMatchInfo;
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
import java.util.stream.Collectors;

public class JarSignatureMapper {
    private int totalClassCount = 0;
    private final SignatureDAO signatureDao;
    private final Logger logger = LoggerFactory.getLogger(JarSignatureMapper.class);

    public JarSignatureMapper(SignatureDAO signatureDao) {
        this.signatureDao = signatureDao;
    }

    public Map<String, Map<String, Object>> inferJarFile(Path jarFilePath) {
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            List<ClassFileInfo> classFileInfos = new ArrayList<>();
            Enumeration<JarEntry> entries = jarFile.entries();
            int classFileCount = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    classFileCount++;
                    ClassFileInfo classFileInfo = processClassFile(entry, jarFile);
                    if (classFileInfo != null) {
                        classFileInfos.add(classFileInfo);
                    }
                }
            }
            totalClassCount = classFileCount;

            return getTopMatches(classFileInfos, signatureDao);
        } catch (IOException e) {
            logger.error("Error while processing JAR file: " + jarFilePath, e);
            throw new RuntimeException(e);
        }
    }

    public Map<String, Map<String, Object>> getTopMatches(List<ClassFileInfo> signatures, SignatureDAO signatureDao) {
        logger.info("Getting top matches for " + signatures.size() + " signatures");
        List<Long> hashes = signatures.stream()
                .map(signature -> signature.getHashCode())
                .collect(Collectors.toList());

        // get the top library matches based on hashes
        List<LibraryMatchInfo> matches = signatureDao.returnTopLibraryMatches(hashes);

        Map<String, LibraryMatchInfo> libraryVersionMap = new HashMap<>();

        if (matches.size() > 0) {
            libraryVersionMap = matches.stream()
                    .collect(Collectors.toMap(
                            match -> match.getGroupId() + ":" + match.getArtifactId(), // key
                            match -> match, // value
                            // merge function, in case of key collision, keep the version with maximum count
                            (existing, newOne) -> existing.getClassFileCount() > newOne.getClassFileCount() ? existing : newOne
                    ));
        }

        // Transform to Map with String keys and Long values
        Map<String, Map<String, Object>> libraryVersionCountMap = libraryVersionMap.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey() + ":" + entry.getValue().getVersion(),
                        entry -> Map.of("count", (long) entry.getValue().getClassFileCount(),
                                "total", (long) entry.getValue().getTotalCount(),
                                "ratio", ((double)entry.getValue().getClassFileCount())/entry.getValue().getTotalCount())));

        return libraryVersionCountMap;
    }

    private ClassFileInfo processClassFile(JarEntry entry, JarFile jarFile) throws IOException {
        logger.info("Processing class file: " + entry.getName());
        try (InputStream classFileInputStream = jarFile.getInputStream(entry)) {
            byte[] bytecode = classFileInputStream.readAllBytes();
            BytecodeDetails bytecodeDetails = BytecodeParser.extractSignature(bytecode);
            // TODO: jsr305 is always the same
            return new ClassFileInfo(entry.getName(), bytecodeDetails.getSignature());
        } catch (Exception e) {
            logger.error("Error while processing class file: " + entry.getName(), e);
            return null;
        }
    }

    public int getTotalClassCount() {
        return totalClassCount;
    }
}
