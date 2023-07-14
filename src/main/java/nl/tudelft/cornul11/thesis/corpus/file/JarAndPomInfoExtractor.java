package nl.tudelft.cornul11.thesis.corpus.file;

public class JarAndPomInfoExtractor {
    String groupId;
    String artifactId;
    String version;

    public JarAndPomInfoExtractor(String jarFilePath) {
        parseJarOrPomFilePath(jarFilePath);
    }

    // TODO: this has to be thoroughly tested, maybe also crosscheck with the pom file for certainty
    private void parseJarOrPomFilePath(String jarFilePath) {
        String[] splitPath = jarFilePath.split("/");

        String version = splitPath[splitPath.length - 2];
        String artifactID = splitPath[splitPath.length - 3];

        // Find the position of '.m2/repository' by comparing adjacent elements in the 'splitPath' array
        int repoIndex = -1;
        for (int i = 0; i < splitPath.length - 1; i++) {
            if (splitPath[i].equals(".m2") && splitPath[i + 1].equals("repository")) {
                repoIndex = i;
                break;
            }
        }

        // Start building the 'groupId' from the position just after '.m2/repository'
        int startIdx = repoIndex + 2;

        StringBuilder groupID = new StringBuilder();
        for (int i = startIdx; i < splitPath.length - 3; i++) {
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
