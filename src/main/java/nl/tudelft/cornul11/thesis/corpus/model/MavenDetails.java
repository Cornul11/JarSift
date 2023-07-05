package nl.tudelft.cornul11.thesis.corpus.model;

public class MavenDetails {
    private final String version;
    private final String groupId;
    private final String artifactId;

    public MavenDetails(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }
}