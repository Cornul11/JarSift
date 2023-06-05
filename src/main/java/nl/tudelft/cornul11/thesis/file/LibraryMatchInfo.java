package nl.tudelft.cornul11.thesis.file;

public class LibraryMatchInfo {
    private String groupId;
    private String artifactId;
    private String version;
    private int classFileCount;


    public LibraryMatchInfo(String groupId, String artifactId, String version, int classFileCount) {
        this.groupId = groupId;
        this.artifactId =artifactId ;
        this.version = version;
        this.classFileCount = classFileCount;
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
}
