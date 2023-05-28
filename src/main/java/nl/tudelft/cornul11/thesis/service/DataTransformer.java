package nl.tudelft.cornul11.thesis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.tudelft.cornul11.thesis.model.MavenCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class DataTransformer {
    private static final Logger logger = LoggerFactory.getLogger(DataTransformer.class);

    private final ObjectMapper objectMapper;

    public DataTransformer() {
        this.objectMapper = new ObjectMapper();
    }

    // TODO: have to test this and add edge cases
    public MavenCoordinates transform(String key) {
        String[] split = key.split("/");
        // string is of the format "groupId-artifactId-version"
        return new MavenCoordinates(split[0], split[1], split[2]);
    }
}
