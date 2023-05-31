package nl.tudelft.cornul11.thesis.commandline;

import nl.tudelft.cornul11.thesis.api.PostRequestClient;
import nl.tudelft.cornul11.thesis.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.jarfile.JarFileExplorer;
import nl.tudelft.cornul11.thesis.jarfile.JarFrequencyAnalyzer;
import nl.tudelft.cornul11.thesis.service.VulnerabilityAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class CommandExecutor {
    private static final Logger logger = LoggerFactory.getLogger(CommandExecutor.class);
    private final OptionsBuilder options;
    private final PostRequestClient postRequestClient;

    public CommandExecutor(OptionsBuilder options, PostRequestClient postRequestClient) {
        this.options = options;
        this.postRequestClient = postRequestClient;
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
            DatabaseManager databaseManager = DatabaseManager.getInstance();
            SignatureDAO signatureDao = databaseManager.getSignatureDao();

            if ("CORPUS_GEN_MODE".equals(mode)) {
                String directoryPath = options.getDirectory();
                if (directoryPath != null) {
                    JarFileExplorer jarFileExplorer = new JarFileExplorer(signatureDao);
                    jarFileExplorer.processFiles(directoryPath, options.getLastPath());
                } else {
                    System.out.println("Directory path is required for CORPUS_GEN_MODE");
                    printHelpMessage();
                }
            } else if ("DETECTION_MODE".equals(mode)) {
                String fileName = options.getFilename();
                if (fileName != null) {
                    JarFrequencyAnalyzer jarFrequencyAnalyzer = new JarFrequencyAnalyzer(signatureDao);
                    Map<String, Map<String, Long>> frequencyMap = jarFrequencyAnalyzer.processJar(fileName);
                    int totalClassCount = jarFrequencyAnalyzer.getTotalClassCount();

                    VulnerabilityAnalyzer vulnerabilityAnalyzer = new VulnerabilityAnalyzer(postRequestClient);

                    vulnerabilityAnalyzer.checkForVulnerability(frequencyMap, totalClassCount);
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