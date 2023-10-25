package nl.tudelft.cornul11.thesis.packaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import nl.tudelft.cornul11.thesis.corpus.model.Dependency;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectMetadata {
    private static final Pattern ANSI_ESCAPE_CODE_PATTERN = Pattern.compile("\u001B\\[[;\\d]*m");

    private final static Logger logger = getLogger(ProjectMetadata.class);
    private static final String META_INF_MAVEN = "META-INF/maven";
    private static final String POM_XML = "pom.xml";

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
        this.effectiveDependencies = cleanDependencies(effectiveDependencies);
    }

    public ProjectMetadata(String projectName, List<Dependency> directDependencies, ShadeConfiguration shadeConfiguration) {
        this(projectName, directDependencies, shadeConfiguration, null);
    }

    private List<Dependency> cleanDependencies(List<Dependency> dependencies) {
        return dependencies.stream()
                .map(dep -> new Dependency(
                        cleanGroupId(dep.getGroupId()),
                        dep.getArtifactId(),
                        dep.getVersion()))
                .collect(Collectors.toList());
    }

    private String cleanGroupId(String groupId) {
        groupId = ANSI_ESCAPE_CODE_PATTERN.matcher(groupId).replaceAll("");
        groupId = groupId.replace("[INFO]", "").trim();
        return groupId;
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

    /**
     * This method should be called after the project has been generated and the effective dependencies have been
     * calculated from mvn dependency:list. Now, maven's resolution algorithm sometimes still includes different versions
     * of some libraries, thus we have to open the jar, look at all pom.xml files in the META-INF/maven folder and
     * update the versions of the dependencies accordingly.
     */
    public void confirmPackages() {
        Path jarPath = constructJarPath();
        if (!jarPath.toFile().exists()) {
            return; // jar file does not exist, it won't be used in the evaluation anyway
        }

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            processJarEntries(jarFile);
        } catch (IOException e) {
            logger.error("Error while opening jar file: " + jarPath, e);
            e.printStackTrace();
        }
    }

    private void processJarEntries(JarFile jarFile) throws IOException {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();

            if (isValidPomEntry(entry)) {


                // split the path into parts
                String[] pathParts = entry.getName().split("/");

                // get the groupId and artifactId from the path
                String groupId = pathParts[2];
                String artifactId = pathParts[3];

                // open the pom.xml file and extract the version from the <version> tag
                String version = null;
                try {
                    version = PomParser.extractVersion(jarFile.getInputStream(entry), artifactId);
                } catch (Exception e) {
                    logger.error("Error while parsing pom.xml file: " + entry.getName() + " in jar file: " + jarFile.getName(), e);
                    e.printStackTrace();
                }

                if (version != null) {
                    updateDependencyVersion(groupId, artifactId, version);
                }
            }
        }
    }

    private void updateDependencyVersion(String groupId, String artifactId, String version) {
        // Find and update the dependency with this version
        Dependency dependency = effectiveDependencies.stream()
                .filter(dep -> dep.getGroupId().equals(groupId) && dep.getArtifactId().equals(artifactId))
                .findFirst().orElse(null);

        if (dependency != null) {
            // if found, update the version of this dependency to the version in the pom.xml file
            dependency.setVersion(version);
        }
    }

    private static boolean isValidPomEntry(JarEntry entry) {
        return entry.getName().startsWith(META_INF_MAVEN) && entry.getName().endsWith(POM_XML);
    }

    private Path constructJarPath() {
        return Path.of("projects", projectName, "target", projectName + "-1.0-SNAPSHOT.jar");
    }
}