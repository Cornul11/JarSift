package nl.tudelft.cornul11.thesis.corpus.model;

public class LibraryInfo {
    String groupId;
    String artifactId;
    String version;

    public LibraryInfo(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getGAV() {
        return groupId + ":" + artifactId + ":" + version;
    }
}