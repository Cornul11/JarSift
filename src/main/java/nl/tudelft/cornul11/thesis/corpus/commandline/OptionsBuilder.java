package nl.tudelft.cornul11.thesis.corpus.commandline;

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

    public String getEvaluationDirectory() {
        return cmd.getOptionValue("e");
    }

    public String getMode() {
        return cmd.getOptionValue("m");
    }

    public String getLastPath() {
        return cmd.getOptionValue("p");
    }

    public String getFilePaths() {
        return cmd.getOptionValue("fp");
    }

    public String getOutput() {
        return cmd.getOptionValue("o");
    }

    public Double getThreshold() {
        String thresholdValue = cmd.getOptionValue("t");
        try {
            double threshold = Double.parseDouble(thresholdValue);
            if (threshold < 0 || threshold > 1) {
                logger.error("Threshold value must be between 0 and 1");
                System.exit(1);
            }
            return threshold;
        } catch (Exception e) {
            logger.error("Invalid threshold value: {}, must be a double value between 0 and 1", thresholdValue);
            return null;
        }
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
                .desc("Specify the operation mode: CORPUS_GEN_MODE, IDENTIFICATION_MODE or EVALUATION_MODE")
                .build());

        options.addOption(Option.builder("p")
                .longOpt("lastPath")
                .hasArg()
                .argName("path")
                .desc("Specify the last path to continue from")
                .build());

        options.addOption(Option.builder("fp")
                .longOpt("filePaths")
                .hasArg()
                .argName("file")
                .desc("Specify the path to a file containing paths for processing")
                .build());

        options.addOption(Option.builder("e")
                .longOpt("evaluationDirectory")
                .hasArg()
                .argName("directory")
                .desc("Specify the directory path for evaluation mode")
                .build());

        options.addOption(Option.builder("o")
                .longOpt("output")
                .hasArg()
                .argName("file")
                .desc("Specify the path to inference output file")
                .build());

        options.addOption(Option.builder("t")
                .longOpt("threshold")
                .hasArg()
                .argName("threshold")
                .desc("Specify the threshold for inference")
                .build());
        return options;
    }
}