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
import java.text.DecimalFormat;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class JarSignatureMapper {
    private final SignatureDAO signatureDao;
    private final Logger logger = LoggerFactory.getLogger(JarSignatureMapper.class);

    public JarSignatureMapper(SignatureDAO signatureDao) {
        this.signatureDao = signatureDao;
    }

    public  Map<String, Long> inferJarFile(Path jarFilePath) {
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
            return getFrequencyMap(classFileCount, classFileInfos, signatureDao);
        } catch (IOException e) {
            logger.error("Error while processing JAR file: " + jarFilePath, e);
            throw new RuntimeException(e);
        }
    }

    public Map<String, Long> getFrequencyMap(int classFileCount, List<ClassFileInfo> signatures, SignatureDAO signatureDao) {
        ArrayList<ClassMatchInfo> matches = new ArrayList<>();

        List<String> hashes = signatures.stream()
                .map(signature -> Long.toString(signature.getHashCode()))
                .toList();

        matches.addAll(signatureDao.returnMatches(hashes));
//        for (ClassFileInfo signature : signatures) {
//            logger.info("Checking signature in database: " + signature.getFileName());
//            matches.addAll(signatureDao.returnMatches(Long.toString(signature.getHashCode())));
//        }

        if (matches.size() > 0) {
            DecimalFormat decimalFormat = new DecimalFormat("#.##");

            Map<String, Long> frequencyMap = matches.stream()
                    .collect(Collectors.groupingBy(f -> f.getJarClassGroupId() + "/" + f.getJarClassArtifactId() + "/" + f.getJarClassVersion(),
                            Collectors.counting()));

            // sort frequencymap by value
            frequencyMap = frequencyMap.entrySet().stream()
                    .sorted((Map.Entry.<String, Long>comparingByValue().reversed()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, java.util.LinkedHashMap::new));

            return frequencyMap;
        }
        return new HashMap<>();
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


}
