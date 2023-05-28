package nl.tudelft.cornul11.thesis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.tudelft.cornul11.thesis.model.MavenDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MavenInfoExtractor {
    private static final Logger logger = LoggerFactory.getLogger(MavenInfoExtractor.class);

    private final ObjectMapper objectMapper;

    public MavenInfoExtractor() {
        this.objectMapper = new ObjectMapper();
    }

    // TODO: have to test this and add edge cases
    public MavenDetails transform(String key) {
        String[] split = key.split("/");
        // string is of the format "groupId-artifactId-version"
        return new MavenDetails(split[0], split[1], split[2]);
    }
}
