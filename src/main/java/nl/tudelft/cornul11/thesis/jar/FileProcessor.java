package nl.tudelft.cornul11.thesis.jar;

import nl.tudelft.cornul11.thesis.database.SignatureDao;
import nl.tudelft.cornul11.thesis.file.FileVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileProcessor {
    private final Logger logger = LoggerFactory.getLogger(FileProcessor.class);
    private final JarFileProcessor jarFileProcessor;


    public FileProcessor(SignatureDao signatureDao) {
        this.jarFileProcessor = new JarFileProcessor(signatureDao);
    }

    public void processFiles(String path) {
        Path rootPath = Paths.get(path);
        try {
            long startTime = System.currentTimeMillis();

            // TODO: add tqdm-like progress bar (maybe)
            logger.info("Processing files in directory: " + rootPath);

            FileVisitor fileVisitor = new FileVisitor(rootPath, jarFileProcessor);
            Files.walkFileTree(rootPath, fileVisitor);

            long endTime = System.currentTimeMillis();
            logger.info("Processed " + fileVisitor.getVisitedFilesCount() + " jar file(s) in " + (endTime - startTime) / 1000 + " seconds (" + (endTime - startTime) + " ms)");
            jarFileProcessor.printIgnoredUberJars();
        } catch (IOException e) {
            logger.error("Error while processing files", e);
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
