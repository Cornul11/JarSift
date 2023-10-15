package nl.tudelft.cornul11.thesis.corpus.jarfile;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public void evaluate(Map<String, List<SignatureDAOImpl.LibraryCandidate>> inferredLibrariesMap) {
        double[] thresholds = {0.5, 0.75, 0.9, 0.95, 0.99, 1.0};
        for (double threshold : thresholds) {
            evaluateThreshold(threshold, inferredLibrariesMap);
        }
    }

    public Map<String, List<SignatureDAOImpl.LibraryCandidate>> inferLibrariesFromJars() {
        Map<String, List<SignatureDAOImpl.LibraryCandidate>> inferredLibrariesMap = new HashMap<>();

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

            List<SignatureDAOImpl.LibraryCandidate> candidateLibraries = jarSignatureMapper.inferJarFileMultithreadedProcess(jarFilePath);

            inferredLibrariesMap.put(jarPath, candidateLibraries);
        }
        return inferredLibrariesMap;
    }

    private void evaluateThreshold(double threshold, Map<String, List<SignatureDAOImpl.LibraryCandidate>> inferredLibrariesMap) {
        logger.info("Evaluating with threshold: " + threshold);

        ThresholdStatistics statsVars = new ThresholdStatistics();
        int totalProjects = inferredLibrariesMap.size();
        int currentProject = 0;

        for (Map.Entry<String, List<SignatureDAOImpl.LibraryCandidate>> entry : inferredLibrariesMap.entrySet()) {
            String jarPath = entry.getKey();
            List<SignatureDAOImpl.LibraryCandidate> candidateLibraries = entry.getValue();

            currentProject++;
            double percentageCompleted = ((double) currentProject / totalProjects) * 100;
            logger.info("Processing {} out of {} ({}% completed)",
                    currentProject, totalProjects,
                    String.format("%.2f", percentageCompleted));

            processProjectFolder(threshold, jarPath, candidateLibraries, statsVars);
        }
        statisticsHandler.storeStatistics(threshold, statsVars);
    }

    private void processProjectFolder(double threshold, String jarPath, List<SignatureDAOImpl.LibraryCandidate> candidateLibraries, ThresholdStatistics statVars) {
        File jarFile = new File(jarPath);
        if (!jarFile.exists()) {
            logger.info("Skipping " + jarPath + " because it does not exist");
            return;
        }

        String projectName = jarFile.getParentFile().getParentFile().getName();
        String metadataFilePath = Paths.get(evaluationDirectory, "projects_metadata", projectName + ".json").toString();

        try {
            ProjectMetadata groundTruth = fetchGroundTruth(metadataFilePath);

            List<SignatureDAOImpl.LibraryCandidate> inferredLibraries = filterLibrariesByThreshold(candidateLibraries, threshold);

            if (inferredLibraries.isEmpty()) {
                logger.info("For " + jarPath + ", no libraries passed the threshold");
            }

            double f1Score = statisticsHandler.calculateF1Score(inferredLibraries, groundTruth);
            statisticsHandler.updateStatisticsForProject(groundTruth, f1Score, statVars, groundTruth.getShadeConfiguration());
            logger.info("F1 Score for {}: {}", jarPath, f1Score);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<SignatureDAOImpl.LibraryCandidate> filterLibrariesByThreshold(List<SignatureDAOImpl.LibraryCandidate> candidateLibraries, double threshold) {
        List<SignatureDAOImpl.LibraryCandidate> filteredList = new ArrayList<>();
        for (SignatureDAOImpl.LibraryCandidate inferredLibrary : candidateLibraries) {
            if (inferredLibrary.getIncludedRatio() > threshold || inferredLibrary.isPerfectMatch()) {
                filteredList.add(inferredLibrary);
            }
        }
        return filteredList;
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