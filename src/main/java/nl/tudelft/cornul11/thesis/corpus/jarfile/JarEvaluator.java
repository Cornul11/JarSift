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
import java.util.List;

public class JarEvaluator {
    private final SignatureDAO signatureDao;
    private final String evaluationDirectory;
    private final Logger logger = LoggerFactory.getLogger(JarEvaluator.class);
    private final StatisticsHandler statisticsHandler;
    private final ObjectMapper objectMapper;

    public JarEvaluator(SignatureDAO signatureDao, String evaluationDirectory) {
        this.signatureDao = signatureDao;
        this.evaluationDirectory = evaluationDirectory;
        this.statisticsHandler = new StatisticsHandler(evaluationDirectory);
        this.objectMapper = new ObjectMapper();
    }

    public void evaluate() {
        JarSignatureMapper jarSignatureMapper = new JarSignatureMapper(signatureDao);
        double[] thresholds = {0.5, 0.75, 0.9, 0.95, 0.99, 1.0};
        for (double threshold : thresholds) {
            evaluateThreshold(threshold, jarSignatureMapper);
        }
    }

    private void evaluateThreshold(double threshold, JarSignatureMapper jarSignatureMapper) {
        logger.info("Evaluating with threshold: " + threshold);

        File[] projectFolders = getProjectFolders();
        if (projectFolders == null) return;

        ThresholdStatistics statsVars = new ThresholdStatistics();
        for (File projectFolder : projectFolders) {
            statsVars.setProcessedCount(statsVars.getProcessedCount() + 1);
            double percentageCompleted = ((double) statsVars.getProcessedCount() / projectFolders.length) * 100;
            logger.info("Processing {} out of {} ({}% completed)",
                    statsVars.getProcessedCount(), projectFolders.length,
                    String.format("%.2f", percentageCompleted));

            processProjectFolder(threshold, jarSignatureMapper, projectFolder, statsVars);
        }
        statisticsHandler.storeStatistics(threshold, statsVars);
    }

    private void processProjectFolder(double threshold, JarSignatureMapper jarSignatureMapper, File projectFolder, ThresholdStatistics statVars) {
        String projectName = projectFolder.getName();
        String jarPath = Paths.get(projectFolder.getAbsolutePath(), "target", projectName + "-1.0-SNAPSHOT.jar").toString();
        String metadataFilePath = Paths.get(evaluationDirectory, "projects_metadata", projectName + ".json").toString();

        Path jarFilePath = Path.of(jarPath);

        if (!jarFilePath.toFile().exists()) {
            logger.info("Skipping " + jarPath + " because it does not exist");
            return;
        }

        try {
            ProjectMetadata groundTruth = fetchGroundTruth(metadataFilePath);
            List<SignatureDAOImpl.LibraryCandidate> candidateLibraries = jarSignatureMapper.inferJarFileMultithreadedProcess(jarFilePath);

            if (candidateLibraries == null) {
                logger.info("Skipping " + jarPath + " because it could not be processed");
                return;
            }

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