package nl.tudelft.cornul11.thesis.file;

public class ClassMatchInfo {
    private String jarClassName;
    private String jarClassVersion;
    private String jarClassArtifactId;
    private String jarClassGroupId;


    public ClassMatchInfo(String jarClassName, String jarClassGroupId, String jarClassArtifactId, String jarClassVersion) {
        this.jarClassName = jarClassName;
        this.jarClassArtifactId = jarClassArtifactId;
        this.jarClassGroupId = jarClassGroupId;
        this.jarClassVersion = jarClassVersion;
    }

    public String getJarClassName() {
        return jarClassName;
    }


    public String getJarClassGroupId() {
        return jarClassGroupId;
    }

    public String getJarClassVersion() {
        return jarClassVersion;
    }

    public String getJarClassArtifactId() {
        return jarClassArtifactId;
    }
}
