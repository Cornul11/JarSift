package nl.tudelft.cornul11.thesis.file;

public class PomInfo {
    String artifactId;
    String version;

    public PomInfo(PomProcessor pomProcessor) {
        this.artifactId = pomProcessor.getArtifactId();
        this.version = pomProcessor.getVersion();
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }
}
