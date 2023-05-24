package nl.tudelft.cornul11.thesis.file;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JarInfo {
    String groupId;
    String artifactId;
    String version;

    public JarInfo(String jarFilePath) {
        parseJarFilePath(jarFilePath);
    }

    // TODO: this has to be thoroughly tested, maybe also crosscheck with the pom file for certainty
    private void parseJarFilePath(String jarFilePath) {
        String[] splitPath = jarFilePath.split("/");

        // the last part of the path is the file, we ignore that
        // the second last element is the version
        // the one before that is the artifactId
        // everything else except for the first element is the groupId

        String version = splitPath[splitPath.length - 2];
        String artifactID = splitPath[splitPath.length - 3];

        StringBuilder groupID = new StringBuilder();
        for (int i = 1; i < splitPath.length - 3; i++) {
            groupID.append(splitPath[i]);
            if (i < splitPath.length - 4) { // don't want a trailing slash
                groupID.append('/');
            }
        }

        this.groupId = groupID.toString().replace("/", ".");
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