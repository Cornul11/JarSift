package nl.tudelft.cornul11.thesis.packaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShadeConfiguration {
    @JsonProperty("relocation")
    private final boolean relocation;

    @JsonProperty("minimizeJar")
    private final boolean minimizeJar;

    @JsonProperty("packagePrefixes")
    private List<String> packagePrefixes = new ArrayList<>();

    @JsonCreator
    public ShadeConfiguration(
            @JsonProperty("relocation") boolean relocation,
            @JsonProperty("minimizeJar") boolean minimizeJar,
            @JsonProperty("packagePrefixes") List<String> packagePrefixes) {
        this.relocation = relocation;
        this.minimizeJar = minimizeJar;
        this.packagePrefixes = (packagePrefixes != null) ? packagePrefixes : new ArrayList<>();
    }

    public ShadeConfiguration(boolean relocation, boolean minimizeJar) {
        this.relocation = relocation;
        this.minimizeJar = minimizeJar;
    }

    public void setPackagePrefixes(List<String> packagePrefixes) {
        this.packagePrefixes = packagePrefixes;
    }

    public static List<ShadeConfiguration> getAllConfigurations() {
        List<ShadeConfiguration> configurations = new ArrayList<>();
        // TODO: disabled createDependencyReducedPom for now, because it is not relevant

        configurations.add(new ShadeConfiguration(false, false));
        configurations.add(new ShadeConfiguration(false, true));
        configurations.add(new ShadeConfiguration(true, false));
        configurations.add(new ShadeConfiguration(true, true));
        return configurations;
    }

    public List<String> getPackagePrefixes() {
        return packagePrefixes;
    }

    public boolean getRelocation() {
        return relocation;
    }

    public boolean isMinimizeJar() {
        return minimizeJar;
    }
}
