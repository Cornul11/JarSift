package nl.tudelft.cornul11.thesis.corpus.model;

import java.util.List;

public class LibraryInfo {
    private final List<Dependency> dependencies;

    public LibraryInfo(List<Dependency> dependencies) {
        this.dependencies = dependencies;
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }

}