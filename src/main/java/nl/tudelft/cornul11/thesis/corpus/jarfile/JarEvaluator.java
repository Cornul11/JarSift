package nl.tudelft.cornul11.thesis.corpus.jarfile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAOImpl;
import nl.tudelft.cornul11.thesis.packaging.ProjectMetadata;
import nl.tudelft.cornul11.thesis.packaging.StatisticsHandler;
import nl.tudelft.cornul11.thesis.packaging.ThresholdStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class JarEvaluator {
    private final SignatureDAO signatureDao;
    private final String evaluationDirectory;
    private final Logger logger = LoggerFactory.getLogger(JarEvaluator.class);
    private final StatisticsHandler statisticsHandler;
    private final ObjectMapper objectMapper;
    private final JarSignatureMapper jarSignatureMapper;

    public JarEvaluator(SignatureDAO signatureDao, String evaluationDirectory) {
        this.signatureDao = signatureDao;
        this.evaluationDirectory = evaluationDirectory;
        this.statisticsHandler = new StatisticsHandler(evaluationDirectory);
        this.objectMapper = new ObjectMapper();
        this.jarSignatureMapper = new JarSignatureMapper(this.signatureDao);
    }

    public void evaluate(Map<String, List<JarEvaluator.InferredLibrary>> inferredLibrariesMap) {
        double[] thresholds = {0.5, 0.75, 0.9, 0.95, 0.99, 1.0};
        for (double threshold : thresholds) {
            evaluateThreshold(threshold, inferredLibrariesMap);
        }
    }

    public Map<String, List<JarEvaluator.InferredLibrary>> inferLibrariesFromJars() {
        try {
            Map<String, List<InferredLibrary>> loadedLibraries = loadInferredLibraries();
            if (!loadedLibraries.isEmpty()) {
                logger.info("Loaded inferred libraries from file");
                return loadedLibraries;
            }
        } catch (IOException e) {
            logger.warn("Failed to load inferred libraries from file. Re-inferring them", e);
        }

        Map<String, List<JarEvaluator.InferredLibrary>> inferredLibrariesMap = new HashMap<>();

        File[] projectFolders = getProjectFolders();
        if (projectFolders == null) return inferredLibrariesMap;

        for (File projectFolder : projectFolders) {
            String projectName = projectFolder.getName();
            String jarPath = Paths.get(projectFolder.getAbsolutePath(), "target", projectName + "-1.0-SNAPSHOT.jar").toString();
            Path jarFilePath = Path.of(jarPath);

            if (!jarFilePath.toFile().exists()) {
                logger.info("Skipping " + jarPath + " because it does not exist");
                continue;
            }

            List<SignatureDAOImpl.LibraryCandidate> libraryCandidates = jarSignatureMapper.inferJarFileMultithreadedProcess(jarFilePath);
            if (libraryCandidates == null) {
                logger.warn("No class file signatures were retrieved from {}", jarFilePath);
                continue;
            }

            List<JarEvaluator.InferredLibrary> candidateLibraries = libraryCandidates.stream()
                    .map(libraryCandidate -> new JarEvaluator.InferredLibrary(libraryCandidate, jarPath))
                    .collect(Collectors.toList());


            inferredLibrariesMap.put(jarPath, candidateLibraries);
        }

        try {
            storeInferredLibraries(inferredLibrariesMap);
        } catch (IOException e) {
            logger.error("Failed to store inferred libraries to file", e);
        }
        return inferredLibrariesMap;
    }

    private void evaluateThreshold(double threshold, Map<String, List<JarEvaluator.InferredLibrary>> inferredLibrariesMap) {
        logger.info("Evaluating with threshold: " + threshold);

        ThresholdStatistics statsVars = new ThresholdStatistics();
        int totalProjects = inferredLibrariesMap.size();
        int currentProject = 0;

        for (Map.Entry<String, List<JarEvaluator.InferredLibrary>> entry : inferredLibrariesMap.entrySet()) {
            String jarPath = entry.getKey();
            List<JarEvaluator.InferredLibrary> candidateLibraries = entry.getValue();

            currentProject++;
            double percentageCompleted = ((double) currentProject / totalProjects) * 100;
            logger.info("Processing {} out of {} ({}% completed)",
                    currentProject, totalProjects,
                    String.format("%.2f", percentageCompleted));

            processProjectFolder(threshold, jarPath, candidateLibraries, statsVars);
        }
        statisticsHandler.storeStatistics(threshold, statsVars);
    }

    private void processProjectFolder(double threshold, String jarName, List<JarEvaluator.InferredLibrary> candidateLibraries, ThresholdStatistics statVars) {
        String projectName = new File(jarName).getParentFile().getParentFile().getName();
        String metadataFilePath = Paths.get(evaluationDirectory, "projects_metadata", projectName + ".json").toString();

        File metadataFile = new File(metadataFilePath);
        if (!metadataFile.exists()) {
            logger.info("Skipping " + jarName + " because " + metadataFilePath + " does not exist");
            return;
        }

        try {
            ProjectMetadata groundTruth = fetchGroundTruth(metadataFilePath);

            List<JarEvaluator.InferredLibrary> inferredLibraries = filterLibrariesByThreshold(candidateLibraries, threshold);

            if (inferredLibraries.isEmpty()) {
                logger.info("For " + jarName + ", no libraries passed the threshold");
            }

            statisticsHandler.updateStatisticsForProject(groundTruth, statVars, inferredLibraries);
            logger.info("F1 Score for {}: {}", jarName, statisticsHandler.getLastF1Score());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<JarEvaluator.InferredLibrary> filterLibrariesByThreshold(List<JarEvaluator.InferredLibrary> candidateLibraries, double threshold) {
        List<JarEvaluator.InferredLibrary> filteredList = new ArrayList<>();
        for (JarEvaluator.InferredLibrary inferredLibrary : candidateLibraries) {
            if (inferredLibrary.getIncludedRatio() > threshold || inferredLibrary.isPerfectMatch()) {
                filteredList.add(inferredLibrary);
            }
        }
        return filteredList;
    }

    private void storeInferredLibraries(Map<String, List<JarEvaluator.InferredLibrary>> inferredLibrariesMap) throws IOException {
        Path filePath = Paths.get(evaluationDirectory, "evaluation", "inferredLibraries.json");

        if (!filePath.getParent().toFile().exists()) {
            filePath.getParent().toFile().mkdirs();
        }

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), inferredLibrariesMap);
        logger.info("Stored inferred libraries to {}", filePath);
    }

    public static class InferredLibrary {
        private String gav;
        private String version;
        private String groupId;
        private String artifactId;
        private String jarPath;
        private double includedRatio;
        private boolean perfectMatch;
        private List<String> alternativeVersions;

        // needed for deserialization
        public InferredLibrary() {
        }


        public InferredLibrary(SignatureDAOImpl.LibraryCandidate libraryCandidate, String jarPath) {
            this.gav = libraryCandidate.getGAV();
            this.version = libraryCandidate.getVersion();
            this.groupId = libraryCandidate.getGroupId();
            this.artifactId = libraryCandidate.getArtifactId();
            this.jarPath = jarPath;
            this.includedRatio = libraryCandidate.getIncludedRatio();
            this.perfectMatch = libraryCandidate.isPerfectMatch();
            this.alternativeVersions = new ArrayList<>(Objects.requireNonNullElseGet(libraryCandidate.getAlternativeVersions(), ArrayList::new));
        }

        public String getGAV() {
            return gav;
        }

        public void setGav(String gav) {
            this.gav = gav;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getJarPath() {
            return jarPath;
        }

        public void setJarPath(String jarPath) {
            this.jarPath = jarPath;
        }

        public double getIncludedRatio() {
            return includedRatio;
        }

        public void setIncludedRatio(double includedRatio) {
            this.includedRatio = includedRatio;
        }

        public boolean isPerfectMatch() {
            return perfectMatch;
        }

        public void setPerfectMatch(boolean perfectMatch) {
            this.perfectMatch = perfectMatch;
        }

        public List<String> getAlternativeVersions() {
            return alternativeVersions;
        }

        public void setAlternativeVersions(List<String> alternativeVersions) {
            this.alternativeVersions = alternativeVersions;
        }
    }

    private Map<String, List<InferredLibrary>> loadInferredLibraries() throws IOException {
        Path filePath = Paths.get(evaluationDirectory, "evaluation", "inferredLibraries.json");
        if (filePath.toFile().exists()) {
            logger.info("Loading inferred libraries from {}", filePath);
            return objectMapper.readValue(filePath.toFile(), new TypeReference<>() {
            });
        }
        return new HashMap<>();
    }

    private File[] getProjectFolders() {
        // TODO: move all hardcoded paths to config or command line arguments
        Path projectsDirectory = Paths.get(evaluationDirectory, "projects");
        return projectsDirectory.toFile().listFiles(File::isDirectory);
    }

    private ProjectMetadata fetchGroundTruth(String metadataFilePath) throws IOException {
        return objectMapper.readValue(Paths.get(metadataFilePath).toFile(), ProjectMetadata.class);
    }
}