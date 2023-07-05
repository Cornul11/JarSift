package nl.tudelft.cornul11.thesis;

import nl.tudelft.cornul11.thesis.corpus.database.DatabaseConfig;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAOImpl;
import nl.tudelft.cornul11.thesis.corpus.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarHandler;
import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.BytecodeDetails;
import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.BytecodeParser;
import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.BytecodeUtils;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;


public class ComparisonSandbox {
    private static final Set<String> FILENAME_EXCEPTIONS = Set.of("module-info.class", "package-info.class");
    private static final Set<String> PREFIX_EXCEPTIONS = Set.of("META-INF/", "META-INF/versions/", "test/");

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage java JarFileAnalyzer <original jar path> <shaded jar path>");
            System.exit(-1);
        }

        // add "detection/" to the beggining of both args
        args[0] = "detection/" + args[0];
        args[1] = "detection/" + args[1];

        ConfigurationLoader config = new ConfigurationLoader();

        DatabaseConfig databaseConfig = config.getDatabaseConfig();
        DatabaseManager databaseManager = DatabaseManager.getInstance(databaseConfig);
        SignatureDAO signatureDao = databaseManager.getSignatureDao();
        // Fetch hashes for a specific artifactId and version from the database
        if (false) {
            List<Long> dbHashesForArtifact = ((SignatureDAOImpl) signatureDao).getHashesForArtifactIdVersion("logback-core", "1.4.0");

            JarHandler jarHandler = new JarHandler(Paths.get(args[0]), new ArrayList<>(), new ArrayList<>(), new ConfigurationLoader());
            List<ClassFileInfo> originalClassFileInfos = jarHandler.extractSignatures();

            JarHandler shadedJarHandler = new JarHandler(Paths.get(args[1]), new ArrayList<>(), new ArrayList<>(), new ConfigurationLoader());
            List<ClassFileInfo> shadedClassFileInfos = shadedJarHandler.extractSignatures();

            // Map of className -> ClassFileInfo for original and shaded jars
            Map<String, ClassFileInfo> originalClassInfoMap = originalClassFileInfos.stream().collect(Collectors.toMap(ClassFileInfo::getClassName, classFileInfo -> classFileInfo));
            Map<String, ClassFileInfo> shadedClassInfoMap = shadedClassFileInfos.stream().collect(Collectors.toMap(ClassFileInfo::getClassName, classFileInfo -> classFileInfo));

            System.out.println("Classes in original jar that do not match with DB hashes:");
            originalClassInfoMap.forEach((className, originalClassFileInfo) -> {
                if (!dbHashesForArtifact.contains(originalClassFileInfo.getHashCode())) {
                    System.out.println(className);
                }
            });

            System.out.println("Classes in shaded jar that do not match with DB hashes:");
            shadedClassInfoMap.forEach((className, shadedClassFileInfo) -> {
                if (!dbHashesForArtifact.contains(shadedClassFileInfo.getHashCode())) {
                    System.out.println(className);
                }
            });

            System.out.println("Classes that have different hashcodes:");
            originalClassInfoMap.forEach((className, originalClassFileInfo) -> {
                if (shadedClassInfoMap.containsKey(className)) {
                    if (originalClassFileInfo.getHashCode() != shadedClassInfoMap.get(className).getHashCode()) {
                        System.out.println(className);
                    }
                }
            });

            System.out.println("Classes that have different CRCs:");
            originalClassInfoMap.forEach((className, originalClassFileInfo) -> {
                if (shadedClassInfoMap.containsKey(className)) {
                    if (originalClassFileInfo.getCrc() != shadedClassInfoMap.get(className).getCrc()) {
                        System.out.println(className);
                    }
                }
            });

            // original jar path
            String jarFilePath = args[0];
            List<ClassFileInfo> classFileInfos = new ArrayList<>();
            try (JarFile jarFile = new JarFile(Paths.get(jarFilePath).toFile())) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();

                    if (!entry.isDirectory() && entryName.endsWith(".class") && FILENAME_EXCEPTIONS.stream().noneMatch(entryName::contains)) {
//                        ClassFileInfo classFileInfo = processClassFileFromInfer(entry, jarFile);
//                        if (classFileInfo != null) {
//                            classFileInfos.add(classFileInfo);
//                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error while processing JAR file: " + jarFilePath);
            }

            // shaded jar path
            String shadedJarFilePath = args[1];
            List<ClassFileInfo> newShadedClassFileInfos = new ArrayList<>();
            try (JarFile jarFile = new JarFile(Paths.get(shadedJarFilePath).toFile())) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();

                    if (shouldSkip(entry)) {
                        continue;
                    }

                    if (isClassFile(entry)) {
//                        ClassFileInfo classFileInfo = processClassFileFromHandler(entry, jarFile);
//
//                        if (classFileInfo != null) {
//                            newShadedClassFileInfos.add(classFileInfo);
//                        }
                    }
                }
            } catch (Exception e) {
            }

            // compare classFileInfos with newShadedClassFileInfos and see which .class files have different hashcodes
            Map<String, ClassFileInfo> classFileInfoMap = classFileInfos.stream().collect(Collectors.toMap(ClassFileInfo::getClassName, classFileInfo -> classFileInfo));
            Map<String, ClassFileInfo> newShadedClassFileInfoMap = newShadedClassFileInfos.stream().collect(Collectors.toMap(ClassFileInfo::getClassName, classFileInfo -> classFileInfo));

            System.out.println("Classes that have different hashcodes:");
            classFileInfoMap.forEach((className, classFileInfo) -> {
                if (newShadedClassFileInfoMap.containsKey(className)) {
                    if (classFileInfo.getHashCode() != newShadedClassFileInfoMap.get(className).getHashCode()) {
                        System.out.println(className);
                    }
                }
            });
        }
        kek(args);
    }


    private static void kek(String[] args) {
        // print the signature of the file that ends with StaxEventRecorder.class from both the original and shaded jars
        String originalJarFilePath = args[0];
        String shadedJarFilePath = args[1];

        BytecodeDetails original = null;
        BytecodeDetails shaded = null;

        try (JarFile jarFile = new JarFile(Paths.get(originalJarFilePath).toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (!entry.isDirectory() && entryName.endsWith(".class") && FILENAME_EXCEPTIONS.stream().noneMatch(entryName::contains)) {
                    if (entryName.endsWith("StaxEventRecorder.class")) {
                        original = processClassFileFromInfer(entry, jarFile);
//                        if (classFileInfo != null) {
//                            System.out.println("Original jar: " + classFileInfo.getHashCode());
//                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error while processing JAR file: " + originalJarFilePath);
            e.printStackTrace();
        }

        try (JarFile jarFile = new JarFile(Paths.get(shadedJarFilePath).toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();


                if (isClassFile(entry)) {
//                    if (entry.getName().endsWith("StaxEventRecorder.class")) {
                        shaded = processClassFileFromHandler(entry, jarFile);

//                        if (classFileInfo != null) {
//                            System.out.println("Shaded jar: " + classFileInfo.getHashCode());
//                        }
//                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (original == null || shaded == null) {
            ;
        }
        System.out.println("BBB");
    }
    private static boolean shouldSkip(JarEntry entry) {
        return matchesPrefixExceptions(entry) || matchesFilenameExceptions(entry);
    }

    private static boolean matchesPrefixExceptions(JarEntry entry) {
        return PREFIX_EXCEPTIONS.stream()
                .anyMatch(prefix -> entry.getName().startsWith(prefix));
    }

    private static boolean matchesFilenameExceptions(JarEntry entry) {
        return FILENAME_EXCEPTIONS.stream()
                .anyMatch(filename -> entry.getName().contains(filename));
    }

    private static boolean isClassFile(JarEntry entry) {
        return !entry.isDirectory() && entry.getName().endsWith(".class");
    }

    private static BytecodeDetails processClassFileFromHandler(JarEntry entry, JarFile jarFile) {
        try (InputStream classFileInputStream = jarFile.getInputStream(entry)) {
            byte[] bytecode = BytecodeUtils.readBytecodeAndCalculateCRCWhenNotAvailable(entry, classFileInputStream);

            BytecodeDetails bytecodeDetails = BytecodeParser.extractSignature(bytecode);
            return bytecodeDetails;
//            return new ClassFileInfo(entry.getName(), BytecodeUtils.getSignatureHash(bytecodeDetails), entry.getCrc());
        } catch (Exception e) {
            return null;
        }
    }

    public static BytecodeDetails processClassFileFromInfer(JarEntry entry, JarFile jarFile) throws IOException {
        try (InputStream classFileInputStream = jarFile.getInputStream(entry)) {
            byte[] bytecode = BytecodeUtils.readBytecodeAndCalculateCRCWhenNotAvailable(entry, classFileInputStream);

            return BytecodeParser.extractSignature(bytecode);
//            return new ClassFileInfo(entry.getName(), BytecodeUtils.getSignatureHash(bytecodeDetails), entry.getCrc());
        } catch (Exception e) {
            System.err.println("Error while processing class file: " + entry.getName());
            return null;
        }
    }
}