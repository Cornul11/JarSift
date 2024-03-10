package nl.tudelft.cornul11.thesis.corpus.commandline;

import nl.tudelft.cornul11.thesis.corpus.database.DatabaseConfig;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarEvaluator;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarFileExplorer;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarFrequencyAnalyzer;
import nl.tudelft.cornul11.thesis.corpus.service.VulnerabilityAnalyzer;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandExecutor {
    private static final Logger logger = LoggerFactory.getLogger(CommandExecutor.class);
    private final OptionsBuilder options;
    private final ConfigurationLoader config;

    public CommandExecutor(OptionsBuilder options, ConfigurationLoader config) {
        this.options = options;
        this.config = config;
    }

    public void run() {
        if (options.hasHelpOption()) {
            System.out.println("Help message");
            printHelpMessage();
            return;
        }

        if (options.hasVersionOption()) {
            System.out.println("Version: alpha");
            printVersion();
            return;
        }

        String mode = options.getMode();
        if (mode != null) {
            DatabaseConfig databaseConfig = config.getDatabaseConfig();
            DatabaseManager databaseManager = DatabaseManager.getInstance(databaseConfig);
            SignatureDAO signatureDao = databaseManager.getSignatureDao(config.getDatabaseMode());

            switch (mode) {
                case "CORPUS_GEN_MODE":
                    JarFileExplorer jarFileExplorer = new JarFileExplorer(signatureDao, config, null);
                    logger.info("Starting CORPUS_GEN_MODE; ignoring uber JARs: " + config.getIgnoreUberJarSignatures());
                    String directoryPath = options.getDirectory();
                    String filePaths = options.getFilePaths();
                    if (directoryPath != null) {
                        jarFileExplorer.processFiles(directoryPath, options.getLastPath());
                    } else if (filePaths != null) {
                        jarFileExplorer.processFilesFromPathListFile(filePaths, options.getLastPath());
                    } else {
                        System.out.println("Directory path or file path(s) is required for CORPUS_GEN_MODE");
                        printHelpMessage();
                    }
                    break;
                case "IDENTIFICATION_MODE":
                    String fileName = options.getFilename();
                    Double threshold = options.getThreshold();
                    if (fileName != null) {
                        JarFrequencyAnalyzer jarFrequencyAnalyzer = new JarFrequencyAnalyzer(signatureDao);
                        Map<String, Map<String, Object>> frequencyMap = jarFrequencyAnalyzer.processJar(fileName);
                        if (frequencyMap == null) {
                            logger.error("Error in processing jar file, ignoring it");
                            return;
                        }
                        int totalClassCount = jarFrequencyAnalyzer.getTotalClassCount();

                        VulnerabilityAnalyzer vulnerabilityAnalyzer = new VulnerabilityAnalyzer(totalClassCount, config, threshold);

                        vulnerabilityAnalyzer.checkForVulnerability(frequencyMap, options.getOutput());
                    } else {
                        System.out.println("File name is required for IDENTIFICATION_MODE");
                        printHelpMessage();
                    }
                    break;
                case "EVALUATION_MODE":
                    String evaluationDirectory = options.getEvaluationDirectory();
                    if (evaluationDirectory != null) {
                        JarEvaluator jarEvaluator = new JarEvaluator(signatureDao, evaluationDirectory);
                        Map<String, JarEvaluator.LibraryLoadResult> inferredLibrariesMap = jarEvaluator.inferLibrariesFromJars(options.getLastPath());
//                        jarEvaluator.evaluate(inferredLibrariesMap);
                    } else {
                        System.out.println("Evaluation directory is required for EVALUATION_MODE");
                        printHelpMessage();
                    }
                    break;
                case "EXTRACT_SIGNATURES":
                    String directoryPathForSignatures = options.getDirectory();
                    String filePathsForSignatures = options.getFilePaths();
                    String basePath = options.getBasePath();
                    if (directoryPathForSignatures == null || filePathsForSignatures == null || basePath == null) {
                        System.out.println("Directory path, file path(s) and base path is required for EXTRACT_SIGNATURES");
                        printHelpMessage();
                    } else {
                        List<Path> jarFilePaths = null;
                        Path filePath = Paths.get(filePathsForSignatures);
                        try {
                            List<String> lines = Files.readAllLines(filePath);
                            jarFilePaths = lines.stream().map(Paths::get).collect(Collectors.toList());
                        } catch (IOException e) {
                            logger.error("Error reading file: " + filePath, e);
                        }

                        if (jarFilePaths == null) {
                            System.out.println("No jar file paths found in the file: " + filePathsForSignatures);
                            return;
                        }
                        JarFileExplorer jarFileExplorerForSignatures = new JarFileExplorer(signatureDao, config, directoryPathForSignatures);

                        jarFileExplorerForSignatures.processFilesToFiles(jarFilePaths, basePath);

                    }
                    break;
                default:
                    System.out.println("Invalid mode specified: " + mode);
                    printHelpMessage();
                    break;
            }
        } else {
            System.out.println("No mode specified");
            printHelpMessage();
        }
    }

    private void printHelpMessage() {
        System.out.println("Help message");
        // TODO: Implement the logic for printing the help message
    }

    private void printVersion() {
        System.out.println("Version: alpha");
        // TODO: Implement the logic for printing the version
    }
}