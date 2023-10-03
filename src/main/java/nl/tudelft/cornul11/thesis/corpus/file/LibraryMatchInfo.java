package nl.tudelft.cornul11.thesis.corpus.file;

import nl.tudelft.cornul11.thesis.corpus.model.Dependency;

public class LibraryMatchInfo extends Dependency {
    private final int classFileCount;

    private final int totalClassFileCount;

    public LibraryMatchInfo(String groupId, String artifactId, String version, int classFileCount, int totalClassFileCount) {
        super(groupId, artifactId, version);
        this.classFileCount = classFileCount;
        this.totalClassFileCount = totalClassFileCount;
    }

    public int getClassFileCount() {
        return classFileCount;
    }

    public double getTotalCount() {
        return totalClassFileCount;
    }
}
