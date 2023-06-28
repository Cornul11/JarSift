package nl.tudelft.cornul11.thesis.model;

public class Signature {
    private final int id;
    private final String fileName;
    private final String hash;
    private final String groupID;
    private final String artifactId;
    private final String version;

    public Signature(int id, String fileName, String hash, String groupID, String artifactId, String version) {
        this.id = id;
        this.fileName = fileName;
        this.hash = hash;
        this.groupID = groupID;
        this.artifactId = artifactId;
        this.version = version;
    }

    public int getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getHash() {
        return hash;
    }

    public String getGroupID() {
        return groupID;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }
}