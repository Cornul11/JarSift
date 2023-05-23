package nl.tudelft.cornul11.thesis.file;

import nl.tudelft.cornul11.thesis.jar.JarFileProcessor;
import nl.tudelft.cornul11.thesis.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.Files.isHidden;

public class FileVisitor extends SimpleFileVisitor<Path> {
    private final Logger logger = LoggerFactory.getLogger(FileVisitor.class);
    private final Path rootPath;
    private final DatabaseManager dbManager;
    private final JarFileProcessor jarFileProcessor;

    public FileVisitor(Path rootPath, DatabaseManager dbManager, JarFileProcessor jarFileProcessor) {
        this.rootPath = rootPath;
        this.dbManager = dbManager;
        this.jarFileProcessor = jarFileProcessor;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        // TODO: maybe verify not by extension but by magic number
        if (!isHidden(file) && file.toString().endsWith(".jar")) {
            logger.info("Processing jar file: " + file);
            jarFileProcessor.processJarFile(file, dbManager);
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

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
        logger.error("Error while visiting file: " + file, exc);
        return FileVisitResult.CONTINUE;
    }
}