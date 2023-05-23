package nl.tudelft.cornul11.thesis.file;

public class JarFileClassMatchInfo {
    private String jarClassName;
    private String jarClassVersion;
    private String jarClassArtifactId;

    public JarFileClassMatchInfo(String jarClassName, String jarClassArtifactId, String jarClassVersion) {
        this.jarClassName = jarClassName;
        this.jarClassArtifactId = jarClassArtifactId;
        this.jarClassVersion = jarClassVersion;
    }

    public String getJarClassName() {
        return jarClassName;
    }

    public String getJarClassVersion() {
        return jarClassVersion;
    }

    public String getJarClassArtifactId() {
        return jarClassArtifactId;
    }
}
