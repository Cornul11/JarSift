package nl.tudelft.cornul11.thesis.jarfile;

import nl.tudelft.cornul11.thesis.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.file.ClassMatchInfo;
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

public class JarSignatureMapper {
    private int totalClassCount = 0;
    private final SignatureDAO signatureDao;
    private final Logger logger = LoggerFactory.getLogger(JarSignatureMapper.class);

    public JarSignatureMapper(SignatureDAO signatureDao) {
        this.signatureDao = signatureDao;
    }

    public Map<String, Map<String, Long>> inferJarFile(Path jarFilePath) {
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

            return getFrequencyMap(classFileInfos, signatureDao);
        } catch (IOException e) {
            logger.error("Error while processing JAR file: " + jarFilePath, e);
            throw new RuntimeException(e);
        }
    }

    public Map<String, Map<String, Long>> getFrequencyMap(List<ClassFileInfo> signatures, SignatureDAO signatureDao) {
        ArrayList<ClassMatchInfo> matches = new ArrayList<>();

        List<String> hashes = signatures.stream()
                .map(signature -> Long.toString(signature.getHashCode()))
                .toList();

        System.out.println(hashes.size());

        matches.addAll(signatureDao.returnLibraryMatches(hashes));
        signatureDao.returnNewLibraryMatches(hashes, 100);

        System.exit(0);
        Map<String, Map<String, Long>> libraryVersionMap = new HashMap<>();

        if (matches.size() > 0) {
            matches.forEach(match -> {
                String library = match.getJarClassGroupId() + ":" + match.getJarClassArtifactId();
                String version = match.getJarClassVersion();

                Map<String, Long> versionMap = libraryVersionMap.getOrDefault(library, new HashMap<>());
                versionMap.put(version, versionMap.getOrDefault(version, 0L) + 1);
                libraryVersionMap.put(library, versionMap);
            });
        }

        return libraryVersionMap;
    }

    private ClassFileInfo processClassFile(JarEntry entry, JarFile jarFile) throws IOException {
        logger.info("Processing class file: " + entry.getName());
        try (InputStream classFileInputStream = jarFile.getInputStream(entry)) {
            byte[] bytecode = classFileInputStream.readAllBytes();
            BytecodeDetails bytecodeDetails = BytecodeParser.extractSignature(bytecode);
            // TODO: jsr305 is always the same
            return new ClassFileInfo(entry.getName(), bytecodeDetails.getSignature());
        }
    }

    public int getTotalClassCount() {
        return totalClassCount;
    }
}
