package nl.tudelft.cornul11.thesis.corpus.jarfile;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAOImpl;
import nl.tudelft.cornul11.thesis.corpus.model.Dependency;
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

    public void evaluate(Map<String, JarEvaluator.LibraryLoadResult> inferredLibrariesMap) {
        double[] thresholds = {0.5, 0.75, 0.9, 0.95, 0.99, 1.0};
        for (double threshold : thresholds) {
            evaluateThreshold(threshold, inferredLibrariesMap);
        }
    }

    public Map<String, LibraryLoadResult> inferLibrariesFromJars(String resumePath) {
        boolean resumeProcessing = (resumePath == null || resumePath.isEmpty());
        Map<String, LibraryLoadResult> inferredLibrariesMap = new HashMap<>();

        File[] projectFolders = getProjectFolders();
        if (projectFolders == null) {
            logger.warn("No projects found in {}", evaluationDirectory);
            return inferredLibrariesMap;
        }


        try {
            Map<String, LibraryLoadResult> loadedLibraries = loadInferredLibraries();
            if (!loadedLibraries.isEmpty()) {
                logger.info("Loaded {} inferred libraries from file out of {}", loadedLibraries.size(), projectFolders.length);
                return loadedLibraries;
            }
        } catch (IOException e) {
            logger.warn("Failed to load inferred libraries from file. Re-inferring them", e);
        }

        int total = projectFolders.length;
        logger.info("Inferring libraries from {} projects", total);

        int current = 0;
        int failedCount = 0;
        for (File projectFolder : projectFolders) {
            String projectName = projectFolder.getName();
            String jarPath = Paths.get(projectFolder.getAbsolutePath(), "target", projectName + "-1.0-SNAPSHOT.jar").toString();

            if (!resumeProcessing) {
                if (!jarPath.equals(resumePath)) {
                    current++;
                    continue;
                }
                resumeProcessing = true;
            }

            current++;

            Path jarFilePath = Path.of(jarPath);
            boolean processedSuccessfully = true;
            if (!jarFilePath.toFile().exists()) {
                logger.info("Skipping " + jarPath + " because it does not exist");
                processedSuccessfully = false;
            } else {
                List<SignatureDAOImpl.LibraryCandidate> libraryCandidates = jarSignatureMapper.inferJarFileMultithreadedProcess(jarFilePath);
                if (libraryCandidates == null) {
                    logger.warn("No class file signatures were retrieved from {}", jarFilePath);
                    processedSuccessfully = false;
                } else {
                    List<JarEvaluator.InferredLibrary> candidateLibraries = libraryCandidates.stream()
                            .map(libraryCandidate -> new JarEvaluator.InferredLibrary(libraryCandidate, jarPath))
                            .collect(Collectors.toList());


                    List<JarEvaluator.NotFoundLibrary> notFoundLibraries = establishNotFounds(candidateLibraries, projectName);
                    inferredLibrariesMap.put(jarPath, new LibraryLoadResult(candidateLibraries, notFoundLibraries));

                    try {
                        storeLibraries(projectName, candidateLibraries, notFoundLibraries);
                    } catch (IOException e) {
                        logger.error("Failed to store inferred libraries to file", e);
                    }
                }
            }

            if (!processedSuccessfully) {
                failedCount++;
            }

            double percentageCompleted = ((double) current / total) * 100;
            logger.info("Processed {} ({} failed) out of {} ({}% completed)",
                    current, failedCount, total,
                    String.format("%.2f", percentageCompleted));
        }

        return inferredLibrariesMap;
    }

    private List<JarEvaluator.NotFoundLibrary> establishNotFounds(List<JarEvaluator.InferredLibrary> inferredLibraries, String projectName) {
        String metadataFilePath = Paths.get(evaluationDirectory, "projects_metadata", projectName + ".json").toString();

        File metadataFile = new File(metadataFilePath);
        if (!metadataFile.exists()) {
            logger.info("Skipping " + projectName + " because " + metadataFilePath + " does not exist");
            return new ArrayList<>();
        }

        List<JarEvaluator.NotFoundLibrary> notFoundLibraries = new ArrayList<>();
        try {
            ProjectMetadata groundTruth = fetchGroundTruth(metadataFilePath);

            for (Dependency dependency : groundTruth.getEffectiveDependencies()) {
                String dependencyGAV = dependency.getGAV();
                boolean found = inferredLibraries.stream().anyMatch(inferredLibrary ->
                        inferredLibrary.getGAV().equals(dependencyGAV) ||
                        inferredLibrary.getAlternativeVersions().contains(dependencyGAV));
                if (!found) {
                    notFoundLibraries.add(new JarEvaluator.NotFoundLibrary(dependency.getGAV(), dependency.getVersion(), dependency.getGroupId(), dependency.getArtifactId()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return notFoundLibraries;
    }

    private void evaluateThreshold(double threshold, Map<String, JarEvaluator.LibraryLoadResult> inferredLibrariesMap) {
        logger.info("Evaluating with threshold: " + threshold);

        ThresholdStatistics statsVars = new ThresholdStatistics();
        int totalProjects = inferredLibrariesMap.size();
        int currentProject = 0;

        for (Map.Entry<String, JarEvaluator.LibraryLoadResult> entry : inferredLibrariesMap.entrySet()) {
            String jarPath = entry.getKey();
            JarEvaluator.LibraryLoadResult libraryLoadResult = entry.getValue();

            currentProject++;
            double percentageCompleted = ((double) currentProject / totalProjects) * 100;
            logger.info("Processing {} out of {} ({}% completed)",
                    currentProject, totalProjects,
                    String.format("%.2f", percentageCompleted));

            processProjectFolder(threshold, jarPath, libraryLoadResult, statsVars);
        }
        statisticsHandler.storeStatistics(threshold, statsVars);
    }

    private void processProjectFolder(double threshold, String jarName, LibraryLoadResult libraryLoadResult, ThresholdStatistics statVars) {
        String projectName = new File(jarName).getParentFile().getParentFile().getName();
        String metadataFilePath = Paths.get(evaluationDirectory, "projects_metadata", projectName + ".json").toString();

        File metadataFile = new File(metadataFilePath);
        if (!metadataFile.exists()) {
            logger.info("Skipping " + jarName + " because " + metadataFilePath + " does not exist");
            return;
        }

        try {
            ProjectMetadata groundTruth = fetchGroundTruth(metadataFilePath);

            List<JarEvaluator.InferredLibrary> inferredLibraries = filterLibrariesByThreshold(libraryLoadResult.getInferredLibraries(), threshold);
            List<JarEvaluator.NotFoundLibrary> notFoundLibraries = libraryLoadResult.getNotFoundLibraries();

            if (inferredLibraries.isEmpty()) {
                logger.info("For " + jarName + ", no libraries passed the threshold");
            }

            statisticsHandler.updateStatisticsForProject(groundTruth, statVars, inferredLibraries, notFoundLibraries);
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

    private void storeLibraries(String projectName, List<JarEvaluator.InferredLibrary> inferredLibraries, List<JarEvaluator.NotFoundLibrary> notFoundLibraries) throws IOException {
        Path filePath = Paths.get(evaluationDirectory, "evaluation", projectName + "_libraries.json");

        if (!filePath.getParent().toFile().exists()) {
            filePath.getParent().toFile().mkdirs();
        }

        Map<String, Object> libraries = new HashMap<>();
        libraries.put("inferredLibraries", inferredLibraries);
        libraries.put("notFoundLibraries", notFoundLibraries);

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), libraries);
        logger.info("Stored inferred libraries to {}", filePath);
    }


    public static class LibraryLoadResult {
        private List<InferredLibrary> inferredLibraries;
        private List<NotFoundLibrary> notFoundLibraries;

        public LibraryLoadResult(List<InferredLibrary> inferredLibraries, List<NotFoundLibrary> notFoundLibraries) {
            this.inferredLibraries = inferredLibraries;
            this.notFoundLibraries = notFoundLibraries;
        }

        public List<InferredLibrary> getInferredLibraries() {
            return inferredLibraries;
        }

        public List<NotFoundLibrary> getNotFoundLibraries() {
            return notFoundLibraries;
        }
    }

    public static class NotFoundLibrary {
        private String gav;
        private String version;
        private String groupId;
        private String artifactId;

        public NotFoundLibrary() {

        }

        public NotFoundLibrary(String gav, String version, String groupId, String artifactId) {
            this.gav = gav;
            this.version = version;
            this.groupId = groupId;
            this.artifactId = artifactId;
        }

        public String getGav() {
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

    private Map<String, LibraryLoadResult> loadInferredLibraries() throws IOException {
        Map<String, LibraryLoadResult> allLibrariesResult = new HashMap<>();
        File evaluationDir = new File(evaluationDirectory, "evaluation");

        File[] files = evaluationDir.listFiles((dir, name) -> name.endsWith("_libraries.json"));
        if (files != null) {
            for (File file : files) {
                String projectName = file.getName().replace("_libraries.json", "");
                JavaType mapType = objectMapper.getTypeFactory().constructParametricType(List.class, String.class, List.class);
                JavaType inferredType = objectMapper.getTypeFactory().constructParametricType(List.class, InferredLibrary.class);
                JavaType notFoundType = objectMapper.getTypeFactory().constructParametricType(List.class, NotFoundLibrary.class);

                Map<String, List<Map<String, Object>>> librariesData = objectMapper.readValue(file, mapType);

                List<InferredLibrary> inferredLibraries = objectMapper.convertValue(librariesData.get("inferredLibraries"), inferredType);
                List<NotFoundLibrary> notFoundLibraries = objectMapper.convertValue(librariesData.get("notFoundLibraries"), notFoundType);

                allLibrariesResult.put(projectName, new LibraryLoadResult(inferredLibraries, notFoundLibraries));

                logger.info("Loaded libraries for {} from {}", projectName, file.getPath());
            }
        }
        return allLibrariesResult;
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