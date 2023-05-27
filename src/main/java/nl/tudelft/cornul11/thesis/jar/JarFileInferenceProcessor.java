package nl.tudelft.cornul11.thesis.jar;

import nl.tudelft.cornul11.thesis.database.SignatureDao;
import nl.tudelft.cornul11.thesis.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.file.JarFileClassMatchInfo;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeClass;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeSignatureExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class JarFileInferenceProcessor {
    private final SignatureDao signatureDao;
    private final Logger logger = LoggerFactory.getLogger(JarFileInferenceProcessor.class);

    public JarFileInferenceProcessor(SignatureDao signatureDao) {
        this.signatureDao = signatureDao;
    }

    public void inferJarFile(Path jarFilePath) {
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
            checkSignatures(classFileCount, classFileInfos, signatureDao);
        } catch (IOException e) {
            logger.error("Error while processing JAR file: " + jarFilePath, e);
            throw new RuntimeException(e);
        }
    }

    public void checkSignatures(int classFileCount, List<ClassFileInfo> signatures, SignatureDao signatureDao) {
        ArrayList<JarFileClassMatchInfo> matches = new ArrayList<>();
        for (ClassFileInfo signature : signatures) {
            logger.info("Checking signature in database: " + signature.getFileName());
            matches.addAll(signatureDao.returnMatches(Long.toString(signature.getHashCode())));
        }

        if (matches.size() > 0) {
            DecimalFormat decimalFormat = new DecimalFormat("#.##");

            Map<String, Long> frequencyMap = matches.stream()
                    .collect(Collectors.groupingBy(f -> f.getJarClassGroupId() + "»" + f.getJarClassArtifactId() + "-" + f.getJarClassVersion(),
                            Collectors.counting()));

            // sort frequencymap by value
            frequencyMap = frequencyMap.entrySet().stream()
                    .sorted((Map.Entry.<String, Long>comparingByValue().reversed()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, java.util.LinkedHashMap::new));

            frequencyMap.forEach((key, value) -> {
                double percentage = (value * 100.0) / classFileCount;
                String percentageString = decimalFormat.format(percentage);
                String status = "✅";
                String output = String.format("ArtifactId-Version: %s, Count: %d / %d (%s%%) %s",
                        key, value, classFileCount, percentageString, status);

                System.out.println(output);
            });
        }
    }

    private ClassFileInfo processClassFile(JarEntry entry, JarFile jarFile) throws IOException {
        logger.info("Processing class file: " + entry.getName());
        try (InputStream classFileInputStream = jarFile.getInputStream(entry)) {
            byte[] bytecode = classFileInputStream.readAllBytes();
            BytecodeClass bytecodeClass = BytecodeSignatureExtractor.extractSignature(bytecode);
            // TODO: jsr305 is always the same
            return new ClassFileInfo(entry.getName(), bytecodeClass.getSignature());
        }
    }


}
