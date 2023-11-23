package nl.tudelft.cornul11.thesis.oracle;


import nl.tudelft.cornul11.thesis.corpus.commandline.OptionsBuilder;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseConfig;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;

import java.nio.file.Path;
import java.nio.file.Paths;

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
            SignatureDAO signatureDao = databaseManager.getSignatureDao(config.getDatabaseMode());

            switch (mode) {
                case "ORACLE_CORPUS_GEN_MODE":
                    String pomPathsFilePath = options.getFilename();
                    if (pomPathsFilePath != null) {
                        Path pathToFile = Paths.get(pomPathsFilePath);
                        PomFilesProcessor app = new PomFilesProcessor(/*config.getNumConsumerThreads()*/    1);
                        app.processPomFiles(pathToFile);

                    } else {
                        System.out.println("File path is required for ORACLE_CORPUS_GEN_MODE");
                        printHelpMessage();
                    }
                    break;
                case "COMPARISON_MODE":
                    String fileName = options.getFilename();
                    if (fileName != null) {
                        OracleInformationComparator comparator = new OracleInformationComparator(signatureDao);
                        comparator.validateUberJar(fileName);
                        if (comparator.getResults() == null) {
                            System.out.println("Error in processing jar file");
                        } else {
                            System.out.println("Results: " + comparator.getResults());
                        }

                    } else {
                        System.out.println("File name is required for COMPARISON_MODE");
                        printHelpMessage();
                    }
                    break;
                case "BATCH_COMPARISON_MODE":
                    String repoPath = options.getDirectory();
                    if (repoPath != null) {
                        OracleInformationComparator comparator = new OracleInformationComparator(signatureDao);
                        comparator.validateUberJars(repoPath);
                        if (comparator.getResults() == null) {
                            System.out.println("Error in the batch validation of jar files");
                        } else {
                            System.out.println("Results: " + comparator.getResults());
                        }
                    } else {
                        System.out.println("M2 repo root path is required for BATCH_COMPARISON_MODE");
                        printHelpMessage();
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
        // Implement the logic for printing the help message
    }

    private void printVersion() {
        System.out.println("Version: alpha");
        // Implement the logic for printing the version
    }
}