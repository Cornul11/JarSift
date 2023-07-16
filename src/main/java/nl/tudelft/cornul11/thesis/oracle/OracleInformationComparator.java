package nl.tudelft.cornul11.thesis.oracle;

import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.file.JarAndPomInfoExtractor;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarFrequencyAnalyzer;
import nl.tudelft.cornul11.thesis.corpus.service.VulnerabilityAnalyzer;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        if (model == null) {
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

        Map<String, String> shadePluginConfigParameters = extractConfigurationParameters(shadePlugin);

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

    private boolean shouldIncludeDependency(Dependency dep, Map<String, String> shadePluginConfigParameters) {
        return false;
    }

    private Map<String, String> extractConfigurationParameters(Plugin shadePlugin) {
        return null;
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
