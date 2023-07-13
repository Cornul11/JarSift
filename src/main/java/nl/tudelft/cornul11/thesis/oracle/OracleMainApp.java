package nl.tudelft.cornul11.thesis.oracle;

import nl.tudelft.cornul11.thesis.corpus.commandline.OptionsBuilder;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OracleMainApp {
    private static final Logger logger = LoggerFactory.getLogger(OracleMainApp.class);
    // todo: generate latex code from the results of this oracle
    // todo: add a field to the db with the number of class files a jar contains
    public static void main(String[] args) {
        OptionsBuilder optionsParser = null;
        try {
            optionsParser = new OptionsBuilder(args);
        } catch (ParseException ex) {
            logger.error("Error parsing command line arguments: " + ex.getMessage());
            System.exit(1);
        }

        ConfigurationLoader config = new ConfigurationLoader();

        OracleCommandExecutor oracleCommandExecutor = new OracleCommandExecutor(optionsParser, config);
        oracleCommandExecutor.run();
    }
}