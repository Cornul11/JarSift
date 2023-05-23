package nl.tudelft.cornul11.thesis;

import nl.tudelft.cornul11.thesis.cli.CommandLineOptions;
import nl.tudelft.cornul11.thesis.cli.CommandRunner;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EntryPoint {
    private static final Logger logger = LoggerFactory.getLogger(EntryPoint.class);

    public static void main(String[] args) {
        CommandLineOptions optionsParser = null;
        try {
            optionsParser = new CommandLineOptions(args);
        } catch (ParseException ex) {
            logger.error("Error parsing command line arguments: " + ex.getMessage());
            System.exit(1);
        }

        CommandRunner commandRunner = new CommandRunner(optionsParser);
        commandRunner.run();
    }
}