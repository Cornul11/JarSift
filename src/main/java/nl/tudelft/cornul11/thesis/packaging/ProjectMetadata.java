package nl.tudelft.cornul11.thesis.packaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import nl.tudelft.cornul11.thesis.corpus.model.Dependency;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectMetadata {
    @JsonProperty("projectName")
    private final String projectName;

    @JsonProperty("dependencies")
    private final List<Dependency> dependencies;

    @JsonProperty("shadeConfiguration")
    private final ShadeConfiguration shadeConfiguration;

    @JsonCreator
    public ProjectMetadata(
            @JsonProperty("projectName") String projectName,
            @JsonProperty("dependencies") List<Dependency> dependencies,
            @JsonProperty("shadeConfiguration") ShadeConfiguration shadeConfiguration) {
        this.projectName = projectName;
        this.dependencies = dependencies;
        this.shadeConfiguration = shadeConfiguration;
    }

    public String getProjectName() {
        return projectName;
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public ShadeConfiguration getShadeConfiguration() {
        return shadeConfiguration;
    }
}
