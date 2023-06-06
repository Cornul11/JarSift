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

    public Map<String, Long> inferJarFile(Path jarFilePath) {
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            List<ClassFileInfo> classFileInfos = new ArrayList<>();
            Enumeration<JarEntry> entries = jarFile.entries();
            int classFileCount = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    classFileCount++;
                    classFileInfos.add(processClassFile(entry, jarFile));
                }
            }
            totalClassCount = classFileCount;

            return getTopMatches(classFileInfos, signatureDao);
        } catch (IOException e) {
            logger.error("Error while processing JAR file: " + jarFilePath, e);
            throw new RuntimeException(e);
        }
    }

    public Map<String, Long> getTopMatches(List<ClassFileInfo> signatures, SignatureDAO signatureDao) {
        List<String> hashes = signatures.stream()
                .map(signature -> Long.toString(signature.getHashCode()))
                .toList();

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
        Map<String, Long> libraryVersionCountMap = libraryVersionMap.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey() + ":" + entry.getValue().getVersion(),
                        entry -> (long) entry.getValue().getClassFileCount()));

        return libraryVersionCountMap;
    }

    private ClassFileInfo processClassFile(JarEntry entry, JarFile jarFile) throws IOException {
        logger.info("Processing class file: " + entry.getName());
        try (InputStream classFileInputStream = jarFile.getInputStream(entry)) {
            byte[] bytecode = classFileInputStream.readAllBytes();
            BytecodeDetails bytecodeDetails = BytecodeParser.extractSignature(bytecode);
            // TODO: jsr305 is always the same
            System.out.println(bytecodeDetails.getSignature());
            return new ClassFileInfo(entry.getName(), bytecodeDetails.getSignature());
        }
    }

    public int getTotalClassCount() {
        return totalClassCount;
    }
}
