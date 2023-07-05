package nl.tudelft.cornul11.thesis.corpus.model;

public class Signature {
    private final int id;
    private final String fileName;
    private final long hash;
    private final long crc;
    private final String groupID;
    private final String artifactId;
    private final String version;

    public Signature(int id, String fileName, long hash, long crc, String groupID, String artifactId, String version) {
        this.id = id;
        this.fileName = fileName;
        this.hash = hash;
        this.crc = crc;
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

    public long getHash() {
        return hash;
    }

    public long getCrc() {
        return crc;
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