package nl.tudelft.cornul11.thesis.packaging;

import nl.tudelft.cornul11.thesis.corpus.model.LibraryInfo;

import java.util.List;

public class ProjectMetadata {
    private final String projectName;
    private final LibraryInfo library;
    private final String relocationParameter;

    public ProjectMetadata(String projectName, LibraryInfo library, String relocationParameter) {
        this.projectName = projectName;
        this.library = library;
        this.relocationParameter = relocationParameter;
    }

    public String getProjectName() {
        return projectName;
    }

    public LibraryInfo getLibrary() {
        return library;
    }

    public String getRelocationParameter() {
        return relocationParameter;
    }
}
