package nl.tudelft.cornul11.thesis.oracle;

import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAOImpl;
import nl.tudelft.cornul11.thesis.corpus.file.JarAndPomInfoExtractor;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarFrequencyAnalyzer;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class OracleInformationComparator {
    private final Logger logger = LoggerFactory.getLogger(OracleInformationComparator.class);
    private final SignatureDAO signatureDAO;
    private int TP = 0, FP = 0, TN = 0, FN = 0, UN = 0;
    private List<SignatureDAOImpl.OracleLibrary> cachedOracleLibraries = null;


    public OracleInformationComparator(SignatureDAO signatureDAO) {
        this.signatureDAO = signatureDAO;
    }

    public void validateUberJar(String uberJarPathString) {
        long startTime = System.currentTimeMillis();
        Path uberJarPath = Paths.get(uberJarPathString);

        List<String> inferredLibraries;
        try {
            inferredLibraries = inferLibraries(uberJarPath);
        } catch (Exception e) {
            logger.error("Error while inferring libraries for " + uberJarPath, e);
            return;
        }

        Model model = getModelByJarPath(uberJarPath);
        if (model == null || model.getBuild() == null || model.getBuild().getPlugins() == null) {
            logger.error("Could not find information for " + uberJarPath);
            return;
        }

        Plugin shadePlugin = model.getBuild().getPlugins()
                .stream()
                .filter(plugin -> plugin.getArtifactId().equals("maven-shade-plugin"))
                .findFirst()
                .orElse(null);

        if (shadePlugin == null) {
            System.err.println("Could not find shade plugin for " + uberJarPath);
            return;
        }

        Map<String, List<String>> shadePluginConfigParameters = extractConfigurationParameters(shadePlugin);

        List<String> dbLibraries = model.getDependencies()
                .stream()
                .filter(dep -> shouldIncludeDependency(dep, shadePluginConfigParameters))
                .map(dep -> dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion())
                .collect(Collectors.toList());


        Iterator<String> allPossibleLibraries = getStringIterator();

        for (String dbLibrary : dbLibraries) {
            if (inferredLibraries.contains(dbLibrary)) {
                TP++;  // true positive: the library is in the db and was correctly inferred
            } else {
                if (signatureDAO.isLibraryInDB(dbLibrary)) {
                    FN++;  // false negative: the library is in the db but was not inferred
                } else {
                    UN++;  // unknown negative: the library is in the db but was not inferred and its signature is not in the db
                }
            }
        }

        String uberJarGAV = getGAVFromPath(uberJarPath);

        for (String inferredLibrary : inferredLibraries) {
            if (!dbLibraries.contains(inferredLibrary) &&
                    !inferredLibrary.equals(uberJarGAV)) {  // add condition to check if inferredLibrary equals input JAR
                FP++;  // false positive: the library is not in the db but was inferred
            }
        }

        while (allPossibleLibraries.hasNext()) {
            String possibleLibrary = allPossibleLibraries.next();
            if (!dbLibraries.contains(possibleLibrary) && !inferredLibraries.contains(possibleLibrary)) {
                TN++;  // true negative: the library is not in the db and was correctly not inferred
            }
        }

        long endTime = System.currentTimeMillis();
        logger.info("Validation of {} took {} s", uberJarPath, (double) (endTime - startTime) / 1000);
    }

    private Iterator<String> getStringIterator() {
        Iterator<nl.tudelft.cornul11.thesis.corpus.model.Dependency> libraryInfoIterator = signatureDAO.getAllPossibleLibraries();

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return libraryInfoIterator.hasNext();
            }

            @Override
            public String next() {
                nl.tudelft.cornul11.thesis.corpus.model.Dependency libraryInfo = libraryInfoIterator.next();
                return libraryInfo.getGroupId() + ":" + libraryInfo.getArtifactId() + ":" + libraryInfo.getVersion();
            }
        };
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
        pattern = pattern.replace(".", "\\.")
                .replace("?", ".")
                .replace("*", ".*");
        if (!pattern.endsWith(":.*")) {
            pattern += ":.*";
        }

        return pattern;
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

    private List<String> inferLibraries(Path jarPath) throws Exception {

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

    private String getGAVFromPath(Path jarPath) {
        JarAndPomInfoExtractor jarAndPomInfoExtractor = new JarAndPomInfoExtractor(jarPath.toString());
        String groupId = jarAndPomInfoExtractor.getGroupId();
        String artifactId = jarAndPomInfoExtractor.getArtifactId();
        String version = jarAndPomInfoExtractor.getVersion();

        return groupId + ":" + artifactId + ":" + version;
    }

    private Model getModelByJarPath(Path jarPath) {
        String GAV = getGAVFromPath(jarPath);
        String[] parts = GAV.split(":");
        return signatureDAO.retrievePluginInfo(parts[0], parts[1], parts[2]);
    }

    public String getResults() {
        return "True Positives (TP): " + TP + "\n" +
                "False Positives (FP): " + FP + "\n" +
                "True Negatives (TN): " + TN + "\n" +
                "False Negatives (FN): " + FN + "\n" +
                "Unknown Negatives (UN): " + UN + "\n";
    }

    public void validateUberJars(String repoPath) {
        TP = 0;
        FP = 0;
        TN = 0;
        FN = 0;
        UN = 0;
        long uberJarCount = 0, correctUberJarCount = 0;
        double totalPrecision = 0, totalAccuracy = 0;

        long startTime = System.currentTimeMillis();

        List<Path> uberJarPaths = getUberJarPaths(repoPath);
        for (Path uberJarPath : uberJarPaths) {
            // jar file does not exist
            if (!new File(uberJarPath.toString()).exists()) {
                continue;
            }

            List<String> inferredLibraries = null;
            try {
                inferredLibraries = inferLibraries(uberJarPath);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error while inferring libraries for " + uberJarPath.toString() + ": " + e.getMessage());
            }

            if (inferredLibraries == null) {
                UN++;
                continue;
            }

            boolean isInferredUberJar = inferredLibraries.size() > 1;

            String uberJarGAV = getGAVFromPath(uberJarPath);
            String[] splitGAV = uberJarGAV.split(":");
            String groupId = splitGAV[0];
            String artifactId = splitGAV[1];
            String version = splitGAV[2];

            boolean isOracleUberJar = false;

            for (SignatureDAOImpl.OracleLibrary oracleLibrary : getCachedOracleLibraries()) {
                if (oracleLibrary.getGroupId().equals(groupId) &&
                        oracleLibrary.getArtifactId().equals(artifactId) &&
                        oracleLibrary.getVersion().equals(version)) {
                    isOracleUberJar = oracleLibrary.isAnUberJar();
                    break;
                }
            }

            if (isInferredUberJar == isOracleUberJar) correctUberJarCount++;
            uberJarCount++;

            if (isInferredUberJar) {
                Model model = getModelByJarPath(uberJarPath);
                if (model == null || model.getBuild() == null || model.getBuild().getPlugins() == null) {
                    logger.error("Could not find information for " + uberJarPath);
                    return;
                }

                Plugin shadePlugin = model.getBuild().getPlugins()
                        .stream()
                        .filter(plugin -> plugin.getArtifactId().equals("maven-shade-plugin"))
                        .findFirst()
                        .orElse(null);

                if (shadePlugin == null) {
                    // TODO: why tho?
                    System.err.println("Could not find shade plugin for " + uberJarPath);
                    uberJarCount--;
                    continue;
                }


                Map<String, List<String>> shadePluginConfigParameters = extractConfigurationParameters(shadePlugin);

                List<String> dbLibraries;
                try {
                    dbLibraries = model.getDependencies()
                            .stream()
                            .filter(dep -> shouldIncludeDependency(dep, shadePluginConfigParameters))
                            .map(dep -> dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion())
                            .collect(Collectors.toList());
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error("Error while extracting dependencies for " + uberJarPath + ": " + e.getMessage());
                    continue;
                }

                Iterator<String> allPossibleLibraries = getStringIterator();

                for (String dbLibrary : dbLibraries) {
                    if (inferredLibraries.contains(dbLibrary)) {
                        TP++;  // true positive: the library is in the db and was correctly inferred
                    } else {
                        if (signatureDAO.isLibraryInDB(dbLibrary)) {
                            FN++;  // false negative: the library is in the db but was not inferred
                        } else {
                            UN++;  // unknown negative: the library is in the db but was not inferred and its signature is not in the db
                        }
                    }
                }

                for (String inferredLibrary : inferredLibraries) {
                    if (!dbLibraries.contains(inferredLibrary) &&
                            !inferredLibrary.equals(uberJarGAV)) {  // add condition to check if inferredLibrary equals input JAR
                        FP++;  // false positive: the library is not in the db but was inferred
                    }
                }

                while (allPossibleLibraries.hasNext()) {
                    String possibleLibrary = allPossibleLibraries.next();
                    if (!dbLibraries.contains(possibleLibrary) && !inferredLibraries.contains(possibleLibrary)) {
                        TN++;  // true negative: the library is not in the db and was correctly not inferred
                    }
                }

                long total = TP + FP + TN + FN + UN;
                double accuracy = total != 0 ? (double) (TP + TN) / total : 1;
                totalAccuracy += accuracy;

                double precision = (TP + FP) != 0 ? (double) TP / (TP + FP) : 1;
                totalPrecision += precision;
            }
            long endTime = System.currentTimeMillis();
            logger.info("Validation of {} took {} s", uberJarPath, (double) (endTime - startTime) / 1000);
        }

        System.out.println("Uber-jar classification accuracy: " + (double) correctUberJarCount / uberJarCount);
        System.out.println("Average library classification accuracy: " + totalAccuracy / uberJarCount);
        System.out.println("Average library classification precision: " + totalPrecision / uberJarCount);


        logger.info("Validation of all uber jars took {} s", (double) (System.currentTimeMillis() - startTime) / 1000);
    }


    private List<SignatureDAOImpl.OracleLibrary> getCachedOracleLibraries() {
        if (cachedOracleLibraries == null) {
            cachedOracleLibraries = signatureDAO.getOracleLibraries();
        }
        return cachedOracleLibraries;
    }

    private Path getPathFromGAV(SignatureDAOImpl.OracleLibrary library, String repoPath) {
        String groupId = library.getGroupId();
        String artifactId = library.getArtifactId();
        String version = library.getVersion();

        String path = String.join(
                "/"
                , groupId.replace(".", "/"),
                artifactId,
                version,
                artifactId + "-" + version + ".jar"
        );

        return Paths.get(repoPath, path);
    }

    private List<Path> getUberJarPaths(String repoPath) {
        return getCachedOracleLibraries().stream().map(oracleLibrary -> getPathFromGAV(oracleLibrary, repoPath)).collect(Collectors.toList());
    }
}
