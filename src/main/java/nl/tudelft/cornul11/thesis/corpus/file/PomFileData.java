package nl.tudelft.cornul11.thesis.corpus.file;

public class PomFileData {
    String artifactId;
    String version;

    public PomFileData(PomFileParser pomFileParser) {
        this.artifactId = pomFileParser.getArtifactId();
        this.version = pomFileParser.getVersion();
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }
}
