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

    @JsonProperty("directDependencies")
    private final List<Dependency> directDependencies;

    @JsonProperty("shadeConfiguration")
    private final ShadeConfiguration shadeConfiguration;

    @JsonProperty("effectiveDependencies")
    private final List<Dependency> effectiveDependencies;

    @JsonCreator
    public ProjectMetadata(
            @JsonProperty("projectName") String projectName,
            @JsonProperty("directDependencies") List<Dependency> directDependencies,
            @JsonProperty("shadeConfiguration") ShadeConfiguration shadeConfiguration,
            @JsonProperty("effectiveDependencies") List<Dependency> effectiveDependencies) {
        this.projectName = projectName;
        this.directDependencies = directDependencies;
        this.shadeConfiguration = shadeConfiguration;
        this.effectiveDependencies = effectiveDependencies;
    }

    public ProjectMetadata(String projectName, List<Dependency> directDependencies, ShadeConfiguration shadeConfiguration) {
        this(projectName, directDependencies, shadeConfiguration, null);
    }

    public String getProjectName() {
        return projectName;
    }

    public List<Dependency> getDirectDependencies() {
        return directDependencies;
    }

    public ShadeConfiguration getShadeConfiguration() {
        return shadeConfiguration;
    }

    public List<Dependency> getEffectiveDependencies() {
        return effectiveDependencies;
    }

    public ProjectMetadata withEffectiveDependencies(List<Dependency> effectiveDependencies) {
        return new ProjectMetadata(projectName, directDependencies, shadeConfiguration, effectiveDependencies);
    }
}
