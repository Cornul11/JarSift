package nl.tudelft.cornul11.thesis.packaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAOImpl;
import nl.tudelft.cornul11.thesis.corpus.model.Dependency;
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

public class StatisticsHandler {
    private static final Logger logger = LoggerFactory.getLogger(StatisticsHandler.class);
    private final String evaluationDirectory;
    private final ObjectMapper objectMapper;

    public StatisticsHandler(String evaluationDirectory) {
        this.evaluationDirectory = evaluationDirectory;
        this.objectMapper = new ObjectMapper();
    }

    public void storeStatistics(double threshold, ThresholdStatistics thresholdStats) {
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
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(statsFilePath), thresholdStats);
        } catch (IOException e) {
            logger.error("Error while saving stats to file: " + statsFilePath, e);
        }
    }

    public double calculateF1Score(List<SignatureDAOImpl.LibraryCandidate> inferredLibraries, ProjectMetadata groundTruth) {
        Set<String> groundTruthLibrariesSet = new HashSet<>(groundTruth.getEffectiveDependencies().stream().map(Dependency::getGAV).toList());
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

    public void updateStatisticsForProject(ProjectMetadata groundTruth, double f1Score, ThresholdStatistics thresholdStats, ShadeConfiguration shadeConfig) {
        thresholdStats.setF1ScoreSum(thresholdStats.getF1ScoreSum() + f1Score);
        thresholdStats.setNonEmptyProjectCount(thresholdStats.getNonEmptyProjectCount() + 1);

        if (groundTruth.getEffectiveDependencies().size() <= 10) {
            thresholdStats.setF1ScoreSumSmall(thresholdStats.getF1ScoreSumSmall() + f1Score);
            thresholdStats.setSmallProjectCount(thresholdStats.getSmallProjectCount() + 1);
        } else {
            thresholdStats.setF1ScoreSumBig(thresholdStats.getF1ScoreSumBig() + f1Score);
            thresholdStats.setBigProjectCount(thresholdStats.getBigProjectCount() + 1);
        }

        if (shadeConfig.isMinimizeJar()) {
            thresholdStats.setTotalF1ScoreMinimizeJarEnabled(thresholdStats.getTotalF1ScoreMinimizeJarEnabled() + f1Score);
            thresholdStats.setTotalProjectsMinimizeJarEnabled(thresholdStats.getTotalProjectsMinimizeJarEnabled() + 1);
        } else {
            thresholdStats.setTotalF1ScoreMinimizeJarDisabled(thresholdStats.getTotalF1ScoreMinimizeJarDisabled() + f1Score);
            thresholdStats.setTotalProjectsMinimizeJarDisabled(thresholdStats.getTotalProjectsMinimizeJarDisabled() + 1);
        }

        if (shadeConfig.getRelocation()) {
            thresholdStats.setTotalF1ScoreRelocationEnabled(thresholdStats.getTotalF1ScoreRelocationEnabled() + f1Score);
            thresholdStats.setTotalProjectsRelocationEnabled(thresholdStats.getTotalProjectsRelocationEnabled() + 1);
        } else {
            thresholdStats.setTotalF1ScoreRelocationDisabled(thresholdStats.getTotalF1ScoreRelocationDisabled() + f1Score);
            thresholdStats.setTotalProjectsRelocationDisabled(thresholdStats.getTotalProjectsRelocationDisabled() + 1);
        }
    }
}
