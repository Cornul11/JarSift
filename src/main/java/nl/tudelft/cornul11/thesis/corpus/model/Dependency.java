package nl.tudelft.cornul11.thesis.corpus.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Dependency {
    @JsonProperty("groupId")
    private String groupId;

    @JsonProperty("artifactId")
    private String artifactId;

    @JsonProperty("version")
    private String version;

    @JsonProperty("presentInDatabase")
    private boolean presentInDatabase;

    @JsonIgnore
    private String gav;

    @JsonCreator
    public Dependency(
            @JsonProperty("groupId") String groupId,
            @JsonProperty("artifactId") String artifactId,
            @JsonProperty("version") String version,
            @JsonProperty("presentInDatabase") boolean presentInDatabase) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.gav = groupId + ":" + artifactId + ":" + version;
        this.presentInDatabase = presentInDatabase;
    }

    public void setPresentInDatabase(boolean presentInDatabase) {
        this.presentInDatabase = presentInDatabase;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public void setVersion(String version) {
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

    public String getGAV() {
        return gav;
    }

    public boolean isPresentInDatabase() {
        return presentInDatabase;
    }

}
