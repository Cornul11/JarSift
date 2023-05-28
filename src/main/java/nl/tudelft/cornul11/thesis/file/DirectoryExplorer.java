package nl.tudelft.cornul11.thesis.file;

import nl.tudelft.cornul11.thesis.jarfile.FileAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.Files.isHidden;

public class DirectoryExplorer extends SimpleFileVisitor<Path> {
    private final Logger logger = LoggerFactory.getLogger(DirectoryExplorer.class);
    private final Path rootPath;
    private final FileAnalyzer fileAnalyzer;
    private int totalFiles = 0;

    public DirectoryExplorer(Path rootPath, FileAnalyzer fileAnalyzer) {
        this.rootPath = rootPath;
        this.fileAnalyzer = fileAnalyzer;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        // TODO: maybe verify not by extension but by magic number
        if (!isHidden(file) && file.toString().endsWith(".jar")) {
            logger.info("Processing jar file: " + file);
            fileAnalyzer.processJarFile(file);
            totalFiles++;
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (isHidden(dir) && !dir.equals(rootPath)) {
            logger.info("Skipping hidden directory: " + dir);
            return FileVisitResult.SKIP_SUBTREE;
        }
        logger.info("Processing directory: " + dir);
        return FileVisitResult.CONTINUE;
    }

    public int getVisitedFilesCount() {
        return totalFiles;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
        logger.error("Error while visiting file: " + file, exc);
        return FileVisitResult.CONTINUE;
    }
}