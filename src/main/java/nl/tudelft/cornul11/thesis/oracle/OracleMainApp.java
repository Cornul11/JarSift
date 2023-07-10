package nl.tudelft.cornul11.thesis.oracle;

import nl.tudelft.cornul11.thesis.corpus.api.PostRequestClient;
import nl.tudelft.cornul11.thesis.corpus.commandline.CommandExecutor;
import nl.tudelft.cornul11.thesis.corpus.commandline.OptionsBuilder;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseConfig;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;
import org.apache.commons.cli.ParseException;
import org.apache.maven.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OracleMainApp {
    private static final Logger logger = LoggerFactory.getLogger(OracleMainApp.class);

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