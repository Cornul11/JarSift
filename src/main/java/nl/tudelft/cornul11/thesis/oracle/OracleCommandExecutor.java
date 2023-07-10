package nl.tudelft.cornul11.thesis.oracle;


import nl.tudelft.cornul11.thesis.corpus.commandline.OptionsBuilder;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseConfig;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarFileExplorer;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarFrequencyAnalyzer;
import nl.tudelft.cornul11.thesis.corpus.service.VulnerabilityAnalyzer;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class OracleCommandExecutor {
    private final OptionsBuilder options;
    private final ConfigurationLoader config;

    public OracleCommandExecutor(OptionsBuilder options, ConfigurationLoader config) {
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
            SignatureDAO signatureDao = databaseManager.getSignatureDao();

            if ("ORACLE_CORPUS_GEN_MODE".equals(mode)) {
                String pomPathsFilePath = options.getFilename();
                if (pomPathsFilePath != null) {
                    Path pathToFile = Paths.get("paths/pom_files.txt");
                    PomFilesProcessor app = new PomFilesProcessor(1);
                    app.processPomFiles(pathToFile);

                } else {
                    System.out.println("File path is required for ORACLE_CORPUS_GEN_MODE");
                    printHelpMessage();
                }
            } else if ("COMPARISON_MODE".equals(mode)) {
                String fileName = options.getFilename();
                if (fileName != null) {
                    JarFrequencyAnalyzer jarFrequencyAnalyzer = new JarFrequencyAnalyzer(signatureDao);
                    Map<String, Map<String, Object>> frequencyMap = jarFrequencyAnalyzer.processJar(fileName);
                    if (frequencyMap == null) {
                        System.out.println("Error in processing jar file, ignoring it");
                    }

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