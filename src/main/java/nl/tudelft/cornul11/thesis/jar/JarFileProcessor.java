package nl.tudelft.cornul11.thesis.jar;

import nl.tudelft.cornul11.thesis.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.file.PomInfo;
import nl.tudelft.cornul11.thesis.file.PomProcessor;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeClass;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeSignatureExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarFileProcessor {
    private final Logger logger = LoggerFactory.getLogger(JarFileProcessor.class);

    public void processJarFile(Path jarFilePath, DatabaseManager dbManager) throws IOException {
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            // TODO: first parse pom.xml file to get library name and version
            // to avoid having to go through all entries of a JAR first to find the pom.xml file
            // and then go through all entries again to find the class files
            // it would make sense to simply cache the signatures before committing them to the database
            // and commit them all at once at the end
            // this would also allow to do a single commit per library
            PomInfo pomInfo = null;

            List<ClassFileInfo> classFileInfos = new ArrayList<>();
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    classFileInfos.add(processClassFile(entry, jarFile));
                } else if(entry.getName().endsWith("pom.xml")) {
                    pomInfo = new PomInfo(new PomProcessor(jarFile.getInputStream(entry)));
                }
            }

            if (pomInfo == null) {
                // TODO: maybe still commit signatures without library and version?
                logger.warn("No pom.xml file found in JAR file: " + jarFilePath);
                return;
            }
            commitSignatures(classFileInfos, dbManager, pomInfo.getArtifactId(), pomInfo.getVersion());
        } catch (ParserConfigurationException e) {
            logger.error("Error while parsing pom.xml file", e);
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private void commitSignatures(List<ClassFileInfo> signatures, DatabaseManager dbManager, String library, String version) {
        for (ClassFileInfo signature : signatures) {
            dbManager.insertSignature(signature.getFileName(), Integer.toString(signature.getHashCode()), library, version);
            logger.info("Inserted signature into database: " + signature.getFileName());
        }

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
