package nl.tudelft.cornul11.thesis;

import nl.tudelft.cornul11.thesis.api.PostRequestClient;
import nl.tudelft.cornul11.thesis.commandline.OptionsBuilder;
import nl.tudelft.cornul11.thesis.commandline.CommandExecutor;

import nl.tudelft.cornul11.thesis.util.ConfigurationLoader;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class MainApp {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);

    public static void main(String[] args) {
        OptionsBuilder optionsParser = null;
        try {
            optionsParser = new OptionsBuilder(args);
        } catch (ParseException ex) {
            logger.error("Error parsing command line arguments: " + ex.getMessage());
            System.exit(1);
        }

//        Properties config = ConfigurationLoader.loadConfig();
        PostRequestClient postRequestClient = new PostRequestClient(/*config.getProperty("apiKey")*/);

        CommandExecutor commandExecutor = new CommandExecutor(optionsParser, postRequestClient);
        commandExecutor.run();
    }
}