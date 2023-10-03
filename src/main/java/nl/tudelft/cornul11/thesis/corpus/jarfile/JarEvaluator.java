package nl.tudelft.cornul11.thesis.corpus.jarfile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAOImpl;
import nl.tudelft.cornul11.thesis.corpus.model.Dependency;
import nl.tudelft.cornul11.thesis.packaging.ProjectMetadata;
import nl.tudelft.cornul11.thesis.packaging.ShadeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JarEvaluator {
    private final SignatureDAO signatureDao;
    private final String evaluationDirectory;
    private final Logger logger = LoggerFactory.getLogger(JarEvaluator.class);

    public JarEvaluator(SignatureDAO signatureDao, String evaluationDirectory) {
        this.signatureDao = signatureDao;
        this.evaluationDirectory = evaluationDirectory;
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


        InitStatVariables statVars = new InitStatVariables();
        File[] projectFolders = getProjectFolders();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode thresholdStats = mapper.createObjectNode();
        ObjectNode interactionStats = mapper.createObjectNode();

        if (projectFolders != null) {
            processProjectFolders(threshold, jarSignatureMapper, projectFolders, statVars, interactionStats, mapper, thresholdStats);
            saveStats(threshold, thresholdStats);
        }
    }

    private void processProjectFolders(double threshold, JarSignatureMapper jarSignatureMapper, File[] projectFolders, InitStatVariables statVars, ObjectNode interactionStats, ObjectMapper mapper, ObjectNode thresholdStats) {
        for (File projectFolder : projectFolders) {
            statVars.processedCount++;
            logger.info("Processing {} out of {} ({}% completed)",
                    statVars.processedCount,
                    projectFolders.length,
                    ((double) statVars.processedCount / projectFolders.length) * 100);

            processProjectFolder(threshold, jarSignatureMapper, statVars, interactionStats, mapper, projectFolder);
        }

        double averageF1ScoreMinimizeJarEnabled = statVars.totalProjectsMinimizeJarEnabled > 0 ? statVars.totalF1ScoreMinimizeJarEnabled / statVars.totalProjectsMinimizeJarEnabled : 0;
        double averageF1ScoreMinimizeJarDisabled = statVars.totalProjectsMinimizeJarDisabled > 0 ? statVars.totalF1ScoreMinimizeJarDisabled / statVars.totalProjectsMinimizeJarDisabled : 0;
        double averageF1ScoreRelocationEnabled = statVars.totalProjectsRelocationEnabled > 0 ? statVars.totalF1ScoreRelocationEnabled / statVars.totalProjectsRelocationEnabled : 0;
        double averageF1ScoreRelocationDisabled = statVars.totalProjectsRelocationDisabled > 0 ? statVars.totalF1ScoreRelocationDisabled / statVars.totalProjectsRelocationDisabled : 0;

        double totalAverageF1Score =
                (averageF1ScoreMinimizeJarEnabled +
                        averageF1ScoreMinimizeJarDisabled +
                        averageF1ScoreRelocationEnabled +
                        averageF1ScoreRelocationDisabled) / 4.0;

        logger.info("Average F1 Score ({} enabled, {} enabled): {}", "minimizeJar", "relocation", String.format("%.2f", averageF1ScoreMinimizeJarEnabled));
        logger.info("Average F1 Score ({} disabled, {} disabled): {}", "minimizeJar", "relocation", String.format("%.2f", averageF1ScoreMinimizeJarDisabled));
        logger.info("Average F1 Score ({} enabled, {} disabled): {}", "minimizeJar", "relocation", String.format("%.2f", averageF1ScoreRelocationEnabled));
        logger.info("Average F1 Score ({} disabled, {} enabled): {}", "minimizeJar", "relocation", String.format("%.2f", averageF1ScoreRelocationDisabled));
        logger.info("Total Average F1 Score: {}", String.format("%.2f", totalAverageF1Score));

        thresholdStats.put("averageF1ScoreMinimizeJarEnabled", averageF1ScoreMinimizeJarEnabled);
        thresholdStats.put("averageF1ScoreMinimizeJarDisabled", averageF1ScoreMinimizeJarDisabled);
        thresholdStats.put("averageF1ScoreRelocationEnabled", averageF1ScoreRelocationEnabled);
        thresholdStats.put("averageF1ScoreRelocationDisabled", averageF1ScoreRelocationDisabled);
        thresholdStats.put("totalAverageF1Score", totalAverageF1Score);
        thresholdStats.set("interactionStats", interactionStats);
    }

    private void processProjectFolder(double threshold, JarSignatureMapper jarSignatureMapper, InitStatVariables statVars, ObjectNode interactionStats, ObjectMapper mapper, File projectFolder) {
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
            List<SignatureDAOImpl.LibraryCandidate> inferredLibraries = jarSignatureMapper.inferJarFileMultithreadedProcess(jarFilePath);

            if (inferredLibraries == null) {
                logger.info("Skipping " + jarPath + " because it could not be processed");
                return;
            }

            inferredLibraries.removeIf(inferredLibrary -> inferredLibrary.getIncludedRatio() < threshold && !inferredLibrary.isPerfectMatch());

            if (inferredLibraries.isEmpty()) {
                logger.info("Skipping " + jarPath + " because no libraries were passed the threshold");
                return;
            }

            Set<String> groundTruthLibrariesSet = new HashSet<>(groundTruth.getDependencies().stream().map(Dependency::getGAV).toList());
            double f1Score = calculateF1Score(inferredLibraries, groundTruthLibrariesSet);

            ShadeConfiguration shadeConfig = groundTruth.getShadeConfiguration();

            String minimizeJarStatus = shadeConfig.isMinimizeJar() ? "enabled" : "disabled";
            String relocationStatus = shadeConfig.getRelocation() ? "enabled" : "disabled";
            String interactionKey = String.format("minimizeJar:%s,relocation:%s", minimizeJarStatus, relocationStatus);
            ObjectNode interactionData = (ObjectNode) interactionStats.get(interactionKey);
            if (interactionData == null) {
                interactionData = mapper.createObjectNode();
                interactionData.put("totalF1Score", 0);
                interactionData.put("totalProjects", 0);
                interactionStats.set(interactionKey, interactionData);
            }
            interactionData.put("totalF1Score", interactionData.get("totalF1Score").asDouble() + f1Score);
            interactionData.put("totalProjects", interactionData.get("totalProjects").asInt() + 1);

            String configDescription = String.format("minimizeJar: %s, relocation: %s", shadeConfig.isMinimizeJar(), shadeConfig.getRelocation());
            if (shadeConfig.isMinimizeJar()) {
                statVars.totalF1ScoreMinimizeJarEnabled += f1Score;
                statVars.totalProjectsMinimizeJarEnabled++;
            } else {
                statVars.totalF1ScoreMinimizeJarDisabled += f1Score;
                statVars.totalProjectsMinimizeJarDisabled++;
            }
            if (shadeConfig.getRelocation()) {
                statVars.totalF1ScoreRelocationEnabled += f1Score;
                statVars.totalProjectsRelocationEnabled++;
            } else {
                statVars.totalF1ScoreRelocationDisabled += f1Score;
                statVars.totalProjectsRelocationDisabled++;
            }

            logger.info("F1 Score for {} ({}): {}", jarPath, configDescription, f1Score);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File[] getProjectFolders() {
        // TODO: move all hardcoded paths to config or command line arguments
        Path projectsDirectory = Paths.get(evaluationDirectory, "projects");
        return projectsDirectory.toFile().listFiles(File::isDirectory);
    }

    private void saveStats(double threshold, ObjectNode thresholdStats) {
        Path statsDirectoryPath = Paths.get(evaluationDirectory, "stats");
        if (Files.notExists(statsDirectoryPath)) {
            try {
                Files.createDirectories(statsDirectoryPath);
            } catch (IOException e) {
                logger.error("Error while creating stats directory: " + statsDirectoryPath, e);
                return;
            }
        }

        String statsFilePath = statsDirectoryPath.resolve("stats_" + threshold + ".json").toString();
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(statsFilePath), thresholdStats);
        } catch (IOException e) {
            logger.error("Error while saving stats to file: " + statsFilePath, e);
        }
    }

    private static double calculateF1Score(List<SignatureDAOImpl.LibraryCandidate> inferredLibraries, Set<String> groundTruthLibrariesSet) {
        Set<String> inferredLibrariesSet = new HashSet<>();
        for (SignatureDAOImpl.LibraryCandidate inferredLibrary : inferredLibraries) {
            inferredLibrariesSet.add(inferredLibrary.getGAV());
        }

        int tp = 0, fp = 0, fn = 0;
        for (String library : inferredLibrariesSet) {
            if (groundTruthLibrariesSet.contains(library)) {
                tp++;
            } else {
                fp++;
            }
        }

        for (String library : groundTruthLibrariesSet) {
            if (!inferredLibrariesSet.contains(library)) {
                fn++;
            }
        }

        double precision = (double) tp / (tp + fp);
        double recall = (double) tp / (tp + fn);

        if (precision == 0 && recall == 0) {
            return 0;
        }
        // f1 score
        return 2 * precision * recall / (precision + recall);
    }

    private ProjectMetadata fetchGroundTruth(String metadataFilePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(Paths.get(metadataFilePath).toFile(), ProjectMetadata.class);
    }

    class InitStatVariables {
        double totalF1ScoreMinimizeJarEnabled = 0, totalF1ScoreMinimizeJarDisabled = 0;
        double totalF1ScoreRelocationEnabled = 0, totalF1ScoreRelocationDisabled = 0;
        int totalProjectsMinimizeJarEnabled = 0, totalProjectsMinimizeJarDisabled = 0;
        int totalProjectsRelocationEnabled = 0, totalProjectsRelocationDisabled = 0;
        int processedCount = 0;
    }
}
