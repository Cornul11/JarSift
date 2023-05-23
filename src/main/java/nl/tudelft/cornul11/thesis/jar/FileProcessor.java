package nl.tudelft.cornul11.thesis.jar;

import nl.tudelft.cornul11.thesis.database.SignatureDao;
import nl.tudelft.cornul11.thesis.file.FileVisitor;
import nl.tudelft.cornul11.thesis.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;

public class FileProcessor {
    private final Logger logger = LoggerFactory.getLogger(FileProcessor.class);
    private final SignatureDao signatureDao;
    private final JarFileProcessor jarFileProcessor;


    public FileProcessor(SignatureDao signatureDao) {
        this.signatureDao = signatureDao;
        this.jarFileProcessor = new JarFileProcessor();
    }

    public void processFiles(String path) {
        Path rootPath = Paths.get(path);
        try {
            // TODO: add tqdm-like progress bar
            Files.walkFileTree(rootPath, new FileVisitor(rootPath, signatureDao, jarFileProcessor));
        } catch (IOException e) {
            logger.error("Error while processing files", e);
        } finally {
            signatureDao.closeConnection();
        }
    }


    private static boolean isJavaClass(Path file) {
        // 0xCAFEBABE is the magic number for Java class files
        try (DataInputStream input = new DataInputStream(new FileInputStream(file.toFile()))) {
            int magic = input.readInt();
            return magic == 0xCAFEBABE;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
