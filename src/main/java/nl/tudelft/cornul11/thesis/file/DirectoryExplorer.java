package nl.tudelft.cornul11.thesis.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.BlockingQueue;

import static java.nio.file.Files.isHidden;

public class DirectoryExplorer extends SimpleFileVisitor<Path> {
    private final Logger logger = LoggerFactory.getLogger(DirectoryExplorer.class);
    private final Path rootPath;
    private int totalFiles = 0;
    private Path lastVisitedPath = null;
    private boolean shouldProcess = false;
    private final BlockingQueue<Path> queue;

    public DirectoryExplorer(BlockingQueue<Path> queue, Path rootPath) {
        this.queue = queue;
        this.rootPath = rootPath;
    }

    public void setLastVisitedPath(Path lastVisitedPath) {
        this.lastVisitedPath = lastVisitedPath;
        this.shouldProcess = lastVisitedPath == null;
    }

    public int getVisitedFilesCount() {
        return totalFiles;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (shouldProcess) {
            if (!isHidden(file) && file.toString().endsWith(".jar")) {
                queue.offer(file);
                logger.info("Added file to the queue: " + file + "; current queue size = " + queue.size());
//                fileAnalyzer.processJarFile(file);
                totalFiles++;
            }
        } else if (file.equals(lastVisitedPath)) {
            shouldProcess = true;
        }

        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        // supposedly this change would make a huge resume boost

        // If directory is hidden and it's not the root, skip
        if (isHidden(dir) && !dir.equals(rootPath)) {
            logger.info("Skipping hidden directory: " + dir);
            return FileVisitResult.SKIP_SUBTREE;
        }

        // If shouldProcess is false and the directory is not on the path to the resume point, skip the whole subtree
        if (!shouldProcess && !lastVisitedPath.startsWith(dir)) {
            return FileVisitResult.SKIP_SUBTREE;
        }

        // If the resume point has been reached or passed, set shouldProcess to true
        if (dir.equals(lastVisitedPath)) {
            shouldProcess = true;
        }

        // Log if we're processing
        if (shouldProcess) {
            logger.info("Processing directory: " + dir);
        }

        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
        logger.error("Error while visiting file: " + file, exc);
        return FileVisitResult.CONTINUE;
    }
}