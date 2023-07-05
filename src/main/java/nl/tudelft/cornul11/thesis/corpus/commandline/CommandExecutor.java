package nl.tudelft.cornul11.thesis.corpus.commandline;

import nl.tudelft.cornul11.thesis.corpus.api.PostRequestClient;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseConfig;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarFileExplorer;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarFrequencyAnalyzer;
import nl.tudelft.cornul11.thesis.corpus.service.VulnerabilityAnalyzer;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;
import nl.tudelft.cornul11.thesis.database.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class CommandExecutor {
    private static final Logger logger = LoggerFactory.getLogger(CommandExecutor.class);
    private final OptionsBuilder options;
    private final PostRequestClient postRequestClient;
    private final ConfigurationLoader config;

    public CommandExecutor(OptionsBuilder options, PostRequestClient postRequestClient, ConfigurationLoader config) {
        this.options = options;
        this.postRequestClient = postRequestClient;
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
            SignatureDAO signatureDao = databaseManager.getSignatureDao();

            if ("CORPUS_GEN_MODE".equals(mode)) {
                String directoryPath = options.getDirectory();
                if (directoryPath != null) {
                    JarFileExplorer jarFileExplorer = new JarFileExplorer(signatureDao, config);
                    jarFileExplorer.processFiles(directoryPath, options.getLastPath());
                } else {
                    System.out.println("Directory path is required for CORPUS_GEN_MODE");
                    printHelpMessage();
                }
            } else if ("DETECTION_MODE".equals(mode)) {
                String fileName = options.getFilename();
                if (fileName != null) {
                    JarFrequencyAnalyzer jarFrequencyAnalyzer = new JarFrequencyAnalyzer(signatureDao);
                    Map<String, Map<String, Object>> frequencyMap = jarFrequencyAnalyzer.processJar(fileName);
                    if (frequencyMap == null) {
                        System.out.println("Error in processing jar file, ignoring it");
                        return;
                    }
                    int totalClassCount = jarFrequencyAnalyzer.getTotalClassCount();

                    VulnerabilityAnalyzer vulnerabilityAnalyzer = new VulnerabilityAnalyzer(postRequestClient, totalClassCount);

                    vulnerabilityAnalyzer.checkForVulnerability(frequencyMap);
                } else {
                    System.out.println("File name is required for DETECTION_MODE");
                    printHelpMessage();
                }
            } else {
                System.out.println("Invalid mode specified: " + mode);
                printHelpMessage();
            }
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