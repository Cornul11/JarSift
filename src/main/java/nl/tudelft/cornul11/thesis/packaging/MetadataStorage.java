package nl.tudelft.cornul11.thesis.packaging;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MetadataStorage {
    private final ObjectMapper objectMapper;
    private final Path storageDirectory;

    public MetadataStorage(Path storageDirectory) {
        this.storageDirectory = storageDirectory;
        this.objectMapper = new ObjectMapper();
    }

    public void storeMetadata(ProjectMetadata metadata) throws IOException {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
        Path filePath = storageDirectory.resolve(metadata.getProjectName() + ".json");
        Files.writeString(filePath, json, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }
}
