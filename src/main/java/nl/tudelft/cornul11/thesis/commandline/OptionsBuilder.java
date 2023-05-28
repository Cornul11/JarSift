package nl.tudelft.cornul11.thesis.commandline;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptionsBuilder {
    private final CommandLine cmd;
    private final Logger logger = LoggerFactory.getLogger(OptionsBuilder.class);

    public OptionsBuilder(String[] args) throws ParseException {
        Options options = buildOptions();

        CommandLineParser parser = new DefaultParser();
        cmd = parser.parse(options, args);
    }

    public boolean hasHelpOption() {
        return cmd.hasOption("h");
    }

    public boolean hasVersionOption() {
        return cmd.hasOption("v");
    }

    public String getFilename() {
        return cmd.getOptionValue("f");
    }

    public String getDirectory() {
        return cmd.getOptionValue("d");
    }

    public String getMode() {
        return cmd.getOptionValue("m");
    }

    private Options buildOptions() {
        Options options = new Options();

        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Print help message")
                .build());

        options.addOption(Option.builder("v")
                .longOpt("version")
                .desc("Print version")
                .build());

        options.addOption(Option.builder("f")
                .longOpt("file")
                .hasArg()
                .argName("filename")
                .desc("Specify the filename for detection mode")
                .build());

        options.addOption(Option.builder("d")
                .longOpt("directory")
                .hasArg()
                .argName("directory")
                .desc("Specify the directory path for corpus generation mode")
                .build());

        options.addOption(Option.builder("m")
                .longOpt("mode")
                .hasArg()
                .argName("mode")
                .desc("Specify the operation mode: CORPUS_GEN_MODE or DETECTION_MODE")
                .build());

        return options;
    }
}