package nl.tudelft.cornul11.thesis.corpus.file;

public class JarAndPomInfoExtractor {
    String groupId;
    String artifactId;
    String version;

    public JarAndPomInfoExtractor(String jarFilePath, String basePath) {
        parseJarOrPomFilePath(jarFilePath, basePath);
    }

    private void parseJarOrPomFilePath(String jarFilePath, String basePath) {
        jarFilePath = jarFilePath.replace("\\", "/");
        if (basePath != null) {
            basePath = basePath.replace("\\", "/").endsWith("/") ? basePath : basePath + "/";
            if (!basePath.isEmpty() && jarFilePath.startsWith(basePath)) {
                jarFilePath = jarFilePath.substring(basePath.length());
            }
        } else {
            String defaultBase = ".m2/repository/";
            int index = jarFilePath.indexOf(defaultBase);
            if (index != -1) {
                jarFilePath = jarFilePath.substring(index + defaultBase.length());
            }
        }

        String[] splitPath = jarFilePath.split("/");

        String version = splitPath[splitPath.length - 2];
        String artifactID = splitPath[splitPath.length - 3];

        StringBuilder groupID = new StringBuilder();
        for (int i = 0; i < splitPath.length - 3; i++) {
            groupID.append(splitPath[i]);
            if (i < splitPath.length - 4) {
                groupID.append('.');
            }
        }

        this.groupId = groupID.toString();
        this.artifactId = artifactID;
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
}
