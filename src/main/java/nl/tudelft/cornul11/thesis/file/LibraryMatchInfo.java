package nl.tudelft.cornul11.thesis.file;

public class LibraryMatchInfo {
    private String groupId;
    private String artifactId;
    private String version;
    private int classFileCount;

    private int totalClassFileCount;

    public LibraryMatchInfo(String groupId, String artifactId, String version, int classFileCount, int totalClassFileCount) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classFileCount = classFileCount;
        this.totalClassFileCount = totalClassFileCount;
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

    public int getClassFileCount() {
        return classFileCount;
    }

    public double getTotalCount() {
        return totalClassFileCount;
    }
}
