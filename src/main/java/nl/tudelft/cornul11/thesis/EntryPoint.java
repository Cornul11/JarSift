package nl.tudelft.cornul11.thesis;

import nl.tudelft.cornul11.thesis.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeClass;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeSignatureExtractor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static java.nio.file.Files.isHidden;

public class EntryPoint {
    private static final DatabaseManager dbManager = DatabaseManager.getInstance();
    private static final Logger logger = LoggerFactory.getLogger(EntryPoint.class);
    private static void processFiles(String path) {
        Path rootPath = Paths.get(path);

        // process the path given in the first argument and recursively go into all sub-dirs and see all files, code below
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (isHidden(file)) {
                        // early return
                        return FileVisitResult.CONTINUE;
                    }

                    // probing the content type is not reliable apparently
                    // says octet-stream for all class files
                    // String contentType = Files.probeContentType(file);

                    // TODO: maybe verify not by extension but by magic number
                    if (file.toString().endsWith(".jar")) {
                        logger.info("Processing jar file: " + file);
                        processJarFile(file);
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
                    // do something with the error
                    logger.error("Error while visiting file: " + file, exc);
                    return FileVisitResult.CONTINUE;
                }

                private void processJarFile(Path jarFilePath) throws IOException {
                    try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
                        Enumeration<JarEntry> entries = jarFile.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            // TODO: maybe verify not by extension but by magic number
                            if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                                logger.info("Processing class file: " + entry.getName());
                                try (InputStream classFileInputStream = jarFile.getInputStream(entry)) {
                                    byte[] bytecode = classFileInputStream.readAllBytes();
                                    BytecodeClass bytecodeClass = BytecodeSignatureExtractor.extractSignature(bytecode);
                                    dbManager.insertSignature(entry.getName(), Integer.toString(bytecodeClass.hashCode()), "apache.maven", "0.0.1beta");
                                    logger.info("Inserted signature into database: " + entry.getName());
                                }
                            }
                        }
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Retrieve all signatures from the database
        if (false) {
            List<DatabaseManager.Signature> signatureList = dbManager.getAllSignatures();
            for (DatabaseManager.Signature signature : signatureList) {
                System.out.println(signature.id() + ", " + signature.fileName() + ", " + signature.hash() + ", " + signature.library() + ", " + signature.version());
            }
        }

        processFiles(args[0]);

        // Close the database connection
        dbManager.closeConnection();
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