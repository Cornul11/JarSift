package nl.tudelft.cornul11.thesis.oracle;

import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.file.JarAndPomInfoExtractor;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarFrequencyAnalyzer;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class OracleInformationComparator {
    private final Logger logger = LoggerFactory.getLogger(OracleInformationComparator.class);
    private final SignatureDAO signatureDAO;

    public OracleInformationComparator(SignatureDAO signatureDAO) {
        this.signatureDAO = signatureDAO;
    }

    public boolean validateUberJar(String uberJarPathString) {
        Path uberJarPath = Paths.get(uberJarPathString);

        List<String> inferredLibraries = inferLibraries(uberJarPath);
        Collections.sort(inferredLibraries);

        Model model = getModelByJarPath(uberJarPath);
        if (model == null || model.getBuild() == null || model.getBuild().getPlugins() == null) {
            System.err.println("Could not find information for " + uberJarPath);
            return false;
        }

        Plugin shadePlugin = model.getBuild().getPlugins()
                .stream()
                .filter(plugin -> plugin.getArtifactId().equals("maven-shade-plugin"))
                .findFirst()
                .orElse(null);

        if (shadePlugin == null) {
            System.err.println("Could not find shade plugin for " + uberJarPath);
            return false;
        }

        Map<String, List<String>> shadePluginConfigParameters = extractConfigurationParameters(shadePlugin);

        List<String> dbLibraries = model.getDependencies()
                .stream()
                .filter(dep -> shouldIncludeDependency(dep, shadePluginConfigParameters))
                .map(dep -> dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion())
                .collect(Collectors.toList());
        Collections.sort(dbLibraries);

        if (dbLibraries.equals(inferredLibraries)) {
            System.out.println("Uber-JAR validation successful for " + uberJarPath);
            return true;
        } else {
            System.err.println("Mismatch found in uber-JAR " + uberJarPath);
            return false;
        }
    }

    private boolean shouldIncludeDependency(Dependency dep, Map<String, List<String>> shadePluginConfigParameters) {
        String depString = dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion();

        // exclude dependencies with 'test' or 'provided' scope
        String scope = dep.getScope();
        if (scope == null) {
            scope = "compile";  // default to 'compile' scope if not specified
        }
        if ("test".equals(scope) || "provided".equals(scope)) {
            return false;
        }

        // check includes and excludes
        if (!matchesAny(depString, convertPatterns(shadePluginConfigParameters.get("include"))) ||
                matchesAny(depString, convertPatterns(shadePluginConfigParameters.get("exclude")))) {
            return false;
        }

        // check filters
        for (String filterArtifact : shadePluginConfigParameters.getOrDefault("filterArtifact", Collections.emptyList())) {
            if (depString.matches(convertPattern(filterArtifact))) {
                if (!matchesAny(depString, convertPatterns(shadePluginConfigParameters.get("filterInclude"))) ||
                        matchesAny(depString, convertPatterns(shadePluginConfigParameters.get("filterExclude")))) {
                    return false;
                }
            }
        }

        // check relocations
        for (int i = 0; i < shadePluginConfigParameters.getOrDefault("relocationPattern", Collections.emptyList()).size(); i++) {
            String pattern = convertPattern(shadePluginConfigParameters.get("relocationPattern").get(i));
            String shadedPattern = shadePluginConfigParameters.get("relocationShadedPattern").get(i);
            if (depString.matches(pattern)) {
                depString = depString.replaceAll(pattern, shadedPattern);
            }
        }

        return true;
    }

    private boolean matchesAny(String str, List<String> patterns) {
        if (patterns != null) {
            for (String pattern : patterns) {
                if (str.matches(pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> convertPatterns(List<String> patterns) {
        if (patterns == null) {
            return null;
        }
        List<String> regexes = new ArrayList<>();
        for (String pattern : patterns) {
            regexes.add(convertPattern(pattern));
        }
        return regexes;
    }

    private String convertPattern(String pattern) {
        return pattern.replace(".", "\\.")
                .replace("?", ".")
                .replace("*", ".*");
    }

    private void extractDom(Map<String, List<String>> configurationParameters, String key, Xpp3Dom parentDom, String childName) {
        if (parentDom != null) {
            Xpp3Dom childDom = parentDom.getChild(childName);
            if (childDom != null) {
                Xpp3Dom[] children = childDom.getChildren();
                for (Xpp3Dom child : children) {
                    configurationParameters.computeIfAbsent(key, k -> new ArrayList<>()).add(child.getValue());
                }
            }
        }
    }

    private Map<String, List<String>> extractConfigurationParameters(Plugin shadePlugin) {
        Map<String, List<String>> configurationParameters = new HashMap<>();

        // extract execution-level configuration parameters
        List<PluginExecution> executions = shadePlugin.getExecutions();
        for (PluginExecution execution : executions) {
            Xpp3Dom config = (Xpp3Dom) execution.getConfiguration();
            if (config != null) {
                extractDom(configurationParameters, config);
            }
        }

        // extract plugin-level configuration parameters
        Xpp3Dom config = (Xpp3Dom) shadePlugin.getConfiguration();
        if (config != null) {
            extractDom(configurationParameters, config);
        }

        return configurationParameters;
    }

    private void extractDom(Map<String, List<String>> configurationParameters, Xpp3Dom config) {
        extractDom(configurationParameters, "include", config.getChild("artifactSet"), "includes");
        extractDom(configurationParameters, "exclude", config.getChild("artifactSet"), "excludes");
        extractDom(configurationParameters, "filterArtifact", config.getChild("filters"), "artifact");
        extractDom(configurationParameters, "filterInclude", config.getChild("filters"), "includes");
        extractDom(configurationParameters, "filterExclude", config.getChild("filters"), "excludes");
        extractDom(configurationParameters, "relocationPattern", config.getChild("relocations"), "pattern");
        extractDom(configurationParameters, "relocationShadedPattern", config.getChild("relocations"), "shadedPattern");
        extractDom(configurationParameters, "transformerImplementation", config.getChild("transformers"), "implementation");
    }

    private List<String> inferLibraries(Path jarPath) {

        final double THRESHOLD = 0.8;
        JarFrequencyAnalyzer jarFrequencyAnalyzer = new JarFrequencyAnalyzer(signatureDAO);
        Map<String, Map<String, Object>> frequencyMap = jarFrequencyAnalyzer.processJar(String.valueOf(jarPath));

        List<String> libsWithHighRatio = new ArrayList<>();

        for (Map.Entry<String, Map<String, Object>> entry : frequencyMap.entrySet()) {
            double ratio = (double) entry.getValue().get("ratio");

            if (ratio >= THRESHOLD) { // TODO: make this a parameter, or make it configurable;
                libsWithHighRatio.add(entry.getKey());
            }
        }
        return libsWithHighRatio.isEmpty() ? null : libsWithHighRatio;
    }

    private Model getModelByJarPath(Path jarPath) {
        String jarName = jarPath.toString();
        JarAndPomInfoExtractor jarAndPomInfoExtractor = new JarAndPomInfoExtractor(jarName);

        String groupId = jarAndPomInfoExtractor.getGroupId();
        String artifactId = jarAndPomInfoExtractor.getArtifactId();
        String version = jarAndPomInfoExtractor.getVersion();

        return signatureDAO.retrievePluginInfo(groupId, artifactId, version);
    }

    public String getResults() {
        return null;
    }
}
