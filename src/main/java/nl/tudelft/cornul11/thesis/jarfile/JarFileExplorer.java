package nl.tudelft.cornul11.thesis.jarfile;

import nl.tudelft.cornul11.thesis.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.file.DirectoryExplorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JarFileExplorer {
    private final Logger logger = LoggerFactory.getLogger(JarFileExplorer.class);
    private final FileAnalyzer fileAnalyzer;


    public JarFileExplorer(SignatureDAO signatureDao) {
        this.fileAnalyzer = new FileAnalyzer(signatureDao);
    }

    public void processFiles(String path, String lastPath) {
        Path rootPath = Paths.get(path);
        Path lastVisitedPath = lastPath != null ? Paths.get(lastPath) : null;
        try {
            long startTime = System.currentTimeMillis();

            // TODO: add tqdm-like progress bar (maybe)
            logger.info("Processing files in directory: " + rootPath);

            DirectoryExplorer directoryExplorer = new DirectoryExplorer(rootPath, fileAnalyzer);
            directoryExplorer.setLastVisitedPath(lastVisitedPath);
            Files.walkFileTree(rootPath, directoryExplorer);

            long endTime = System.currentTimeMillis();
            logger.info("Processed " + directoryExplorer.getVisitedFilesCount() + " jar file(s) in " + (endTime - startTime) / 1000 + " seconds (" + (endTime - startTime) + " ms)");
            fileAnalyzer.printIgnoredUberJars();
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
