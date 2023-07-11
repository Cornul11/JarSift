package nl.tudelft.cornul11.thesis.oracle;

import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarFrequencyAnalyzer;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
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
            System.err.println("Could not information for " + uberJarPath);
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

        List<String> dbLibraries = shadePlugin.getDependencies()
                .stream()
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

    private List<String> inferLibraries(Path jarPath) {
        JarFrequencyAnalyzer jarFrequencyAnalyzer = new JarFrequencyAnalyzer(signatureDAO);
        Map<String, Map<String, Object>> frequencyMap = jarFrequencyAnalyzer.processJar(String.valueOf(jarPath));

        return frequencyMap.entrySet()
                .stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue().get("version"))
                .collect(Collectors.toList());
    }

    private Model getModelByJarPath(Path jarPath) {
        return null;
    }

    public String getResults() {
        return null;
    }
}
