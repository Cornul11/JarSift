package nl.tudelft.cornul11.thesis.cli;

import nl.tudelft.cornul11.thesis.api.ApiClient;
import nl.tudelft.cornul11.thesis.database.SignatureDao;
import nl.tudelft.cornul11.thesis.jar.FileProcessor;
import nl.tudelft.cornul11.thesis.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.jar.JarInferenceProcessor;
import nl.tudelft.cornul11.thesis.service.DataTransformer;
import nl.tudelft.cornul11.thesis.service.VulnerabilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class CommandRunner {
    private static final Logger logger = LoggerFactory.getLogger(CommandRunner.class);
    private final CommandLineOptions options;
    private final ApiClient apiClient;

    public CommandRunner(CommandLineOptions options, ApiClient apiClient) {
        this.options = options;
        this.apiClient = apiClient;
    }

    public void run() {
        if (options.hasHelpOption()) {
            System.out.println("Help message");
//                printHelpMessage();
            return;
        }

        if (options.hasVersionOption()) {
            System.out.println("Version: alpha");
//                printVersion();
            return;
        }

        String mode = options.getMode();
        if (mode != null) {
            DatabaseManager databaseManager = DatabaseManager.getInstance();
            SignatureDao signatureDao = databaseManager.getSignatureDao();

            if ("CORPUS_GEN_MODE".equals(mode)) {
                String directoryPath = options.getDirectory();
                if (directoryPath != null) {
                    FileProcessor fileProcessor = new FileProcessor(signatureDao);
                    fileProcessor.processFiles(directoryPath);
                } else {
                    System.out.println("Directory path is required for CORPUS_GEN_MODE");
                    printHelpMessage();
                }
            } else if ("DETECTION_MODE".equals(mode)) {
                String fileName = options.getFilename();
                if (fileName != null) {
                    JarInferenceProcessor jarInferenceProcessor = new JarInferenceProcessor(signatureDao);
                    Map<String, Long> frequencyMap = jarInferenceProcessor.processJar(fileName);

                    VulnerabilityService vulnerabilityService = new VulnerabilityService(apiClient);

                    try {
                        vulnerabilityService.checkForVulnerability(frequencyMap);
                    } catch (IOException e) {
                        logger.error("Error while detecting vulnerabilities: " + e.getMessage());
                    }
                    // performDetectionMode(fileName);
                } else {
                    System.out.println("File name is required for DETECTION_MODE");
                    printHelpMessage();
                }
            } else {
                System.out.println("Invalid mode specified: " + mode);
                printHelpMessage();
            }

            signatureDao.closeConnection();
        } else {
            System.out.println("No mode specified");
            printHelpMessage();
        }
    }

    private void printHelpMessage() {
        System.out.println("Help message");
        // Implement the logic for printing the help message
    }

    private void printVersion() {
        System.out.println("Version: alpha");
        // Implement the logic for printing the version
    }
}