package nl.tudelft.cornul11.thesis.cli;

import nl.tudelft.cornul11.thesis.jar.FileProcessor;
import nl.tudelft.cornul11.thesis.jar.JarFileProcessor;
import nl.tudelft.cornul11.thesis.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.jar.JarInferenceProcessor;

public class CommandRunner {
    private final CommandLineOptions options;

    public CommandRunner(CommandLineOptions options) {
        this.options = options;
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
            if ("CORPUS_GEN_MODE".equals(mode)) {
                String directoryPath = options.getDirectory();
                if (directoryPath != null) {
                    FileProcessor fileProcessor = new FileProcessor(DatabaseManager.getInstance());
                    fileProcessor.processFiles(directoryPath);
                } else {
                    System.out.println("Directory path is required for CORPUS_GEN_MODE");
                    printHelpMessage();
                }
            } else if ("DETECTION_MODE".equals(mode)) {
                String fileName = options.getFilename();
                if (fileName != null) {
                    JarInferenceProcessor jarInferenceProcessor = new JarInferenceProcessor(DatabaseManager.getInstance());
                    jarInferenceProcessor.processJar(fileName);

                    // performDetectionMode(fileName);
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