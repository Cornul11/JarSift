package nl.tudelft.cornul11.thesis.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigurationLoader {
    private static final String CONFIG_FILE_NAME="config.properties";

    public static Properties loadConfig() {
        Properties config = new Properties();
        try (InputStream input = new FileInputStream(CONFIG_FILE_NAME)) {
            config.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Error while loading config file: " + CONFIG_FILE_NAME, e);
        }
        return config;
    }
}
