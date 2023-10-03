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

    @JsonProperty("createDependencyReducedPom")
    private final boolean createDependencyReducedPom;

    @JsonProperty("packagePrefixes")
    private List<String> packagePrefixes = new ArrayList<>();

    @JsonCreator
    public ShadeConfiguration(
            @JsonProperty("relocation") boolean relocation,
            @JsonProperty("minimizeJar") boolean minimizeJar,
            @JsonProperty("createDependencyReducedPom") boolean createDependencyReducedPom,
            @JsonProperty("packagePrefixes") List<String> packagePrefixes) {
        this.relocation = relocation;
        this.minimizeJar = minimizeJar;
        this.createDependencyReducedPom = createDependencyReducedPom;
        this.packagePrefixes = (packagePrefixes != null) ? packagePrefixes : new ArrayList<>();
    }

    public ShadeConfiguration(boolean relocation, boolean minimizeJar, boolean createDependencyReducedPom) {
        this.relocation = relocation;
        this.minimizeJar = minimizeJar;
        this.createDependencyReducedPom = createDependencyReducedPom;
    }

    public void setPackagePrefixes(List<String> packagePrefixes) {
        this.packagePrefixes = packagePrefixes;
    }

    public static List<ShadeConfiguration> getAllConfigurations() {
        List<ShadeConfiguration> configurations = new ArrayList<>();
        // TODO: disabled createDependencyReducedPom for now, because it is not relevant

        // no params, just a simple uber-jar
        configurations.add(new ShadeConfiguration(false, false, false));
        // minimize jar
        configurations.add(new ShadeConfiguration(false, true, false));
//        // create dependency reduced pom
//        configurations.add(new ShadeConfiguration(false, false, true));
//        // minimize jar and create dependency reduced pom
//        configurations.add(new ShadeConfiguration(false, true, true));
        // relocation
        configurations.add(new ShadeConfiguration(true, false, false));
        // relocation and minimize jar
        configurations.add(new ShadeConfiguration(true, true, false));
//        // relocation and create dependency reduced pom
//        configurations.add(new ShadeConfiguration(true, false, true));
//        // relocation, minimize jar and create dependency reduced pom
//        configurations.add(new ShadeConfiguration(true, true, true));
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

    public boolean isCreateDependencyReducedPom() {
        return createDependencyReducedPom;
    }
}
