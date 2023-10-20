package nl.tudelft.cornul11.thesis.packaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarEvaluator;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StatisticsHandler {
    private static final Logger logger = LoggerFactory.getLogger(StatisticsHandler.class);
    private double lastF1Score;
    private final String evaluationDirectory;
    private final ObjectMapper objectMapper;

    public StatisticsHandler(String evaluationDirectory) {
        this.evaluationDirectory = evaluationDirectory;
        this.objectMapper = new ObjectMapper();
    }

    public void storeStatistics(double threshold, ThresholdStatistics thresholdStats) {
        Path statsDirectoryPath = Paths.get(evaluationDirectory, "evaluation", "stats");
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

    public double calculateF1Score(double precision, double recall) {
        if (precision + recall == 0) {
            return 0;
        }

        return 2 * precision * recall / (precision + recall);
    }

    private Map<String, Set<String>> convertToMapWithAlternatives(List<JarEvaluator.InferredLibrary> libraries) {
        return libraries.stream()
                .collect(Collectors.toMap(
                        JarEvaluator.InferredLibrary::getGAV,
                        lib -> new HashSet<>(lib.getAlternativeVersions())
                ));
    }

    private Set<String> convertToSet(List<JarEvaluator.InferredLibrary> libraries) {
        return libraries.stream().map(JarEvaluator.InferredLibrary::getGAV).collect(Collectors.toSet());
    }

    private int computeTruePositive(Map<String, Set<String>> inferredLibrariesMap, Set<String> groundTruthLibrariesSet) {
        int tp = 0;
        // TODO: check if library is present in the db
        for (String primaryGAV : inferredLibrariesMap.keySet()) {
            if (groundTruthLibrariesSet.contains(primaryGAV)) {
                tp++;
            } else {
                for (String alternativeGAV : inferredLibrariesMap.get(primaryGAV)) {
                    if (groundTruthLibrariesSet.contains(alternativeGAV)) {
                        tp++;
                        break; // No need to keep checking other alternatives for this primaryGAV
                    }
                }
            }
        }
        return tp;
    }

    private int computeFalsePositive(Set<String> inferredLibrariesSet, Set<String> groundTruthLibrariesSet) {
        int fp = 0;
        for (String library : inferredLibrariesSet) {
            if (!groundTruthLibrariesSet.contains(library)) {
                fp++;
            }
        }
        return fp;
    }

    private int computeFalseNegative(Set<String> inferredLibrariesSet, Set<String> groundTruthLibrariesSet) {
        int fn = 0;
        for (String library : groundTruthLibrariesSet) {
            if (!inferredLibrariesSet.contains(library)) {
                fn++;
            }
        }
        return fn;
    }

    private double calculatePrecision(List<JarEvaluator.InferredLibrary> inferredLibraries, ProjectMetadata groundTruth) {
        Set<String> groundTruthLibrariesSet = new HashSet<>(groundTruth.getEffectiveDependencies().stream().map(Dependency::getGAV).toList());
        Map<String, Set<String>> inferredLibrariesMap = convertToMapWithAlternatives(inferredLibraries);

        int tp = computeTruePositive(inferredLibrariesMap, groundTruthLibrariesSet);
        int fp = computeFalsePositive(inferredLibrariesMap.keySet(), groundTruthLibrariesSet);


        if (tp + fp == 0) {
            return 0;
        }
        // f1 score
        return (double) tp / (tp + fp);
    }

    private double calculateRecall(List<JarEvaluator.InferredLibrary> inferredLibraries, ProjectMetadata groundTruth) {
        Set<String> groundTruthLibrariesSet = new HashSet<>(groundTruth.getEffectiveDependencies().stream().map(Dependency::getGAV).toList());
        Map<String, Set<String>> inferredLibrariesMap = convertToMapWithAlternatives(inferredLibraries);

        int tp = computeTruePositive(inferredLibrariesMap, groundTruthLibrariesSet);
        int fn = computeFalseNegative(inferredLibrariesMap.keySet(), groundTruthLibrariesSet);

        if (tp + fn == 0) {
            return 0;
        }
        // f1 score
        return (double) tp / (tp + fn);
    }

    public double getLastF1Score() {
        return lastF1Score;
    }

    public void updateStatisticsForProject(ProjectMetadata groundTruth, ThresholdStatistics thresholdStats, List<JarEvaluator.InferredLibrary> inferredLibraries) {
        ShadeConfiguration shadeConfig = groundTruth.getShadeConfiguration();
        double precision = calculatePrecision(inferredLibraries, groundTruth);
        double recall = calculateRecall(inferredLibraries, groundTruth);
        double f1Score = calculateF1Score(precision, recall);
        lastF1Score = f1Score;

        thresholdStats.setF1ScoreSum(thresholdStats.getF1ScoreSum() + f1Score);
        thresholdStats.setNonEmptyProjectCount(thresholdStats.getNonEmptyProjectCount() + 1);
        thresholdStats.setF1ScoreAverage(thresholdStats.getF1ScoreSum() / thresholdStats.getNonEmptyProjectCount());

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
            thresholdStats.setPrecisionMinimizeJarEnabled(thresholdStats.getPrecisionMinimizeJarEnabled() + precision);
            thresholdStats.setRecallMinimizeJarEnabled(thresholdStats.getRecallMinimizeJarEnabled() + recall);
        } else {
            thresholdStats.setTotalF1ScoreMinimizeJarDisabled(thresholdStats.getTotalF1ScoreMinimizeJarDisabled() + f1Score);
            thresholdStats.setTotalProjectsMinimizeJarDisabled(thresholdStats.getTotalProjectsMinimizeJarDisabled() + 1);
            thresholdStats.setPrecisionMinimizeJarDisabled(thresholdStats.getPrecisionMinimizeJarDisabled() + precision);
            thresholdStats.setRecallMinimizeJarDisabled(thresholdStats.getRecallMinimizeJarDisabled() + recall);
        }

        if (shadeConfig.getRelocation()) {
            thresholdStats.setTotalF1ScoreRelocationEnabled(thresholdStats.getTotalF1ScoreRelocationEnabled() + f1Score);
            thresholdStats.setTotalProjectsRelocationEnabled(thresholdStats.getTotalProjectsRelocationEnabled() + 1);
            thresholdStats.setPrecisionRelocationEnabled(thresholdStats.getPrecisionRelocationEnabled() + precision);
            thresholdStats.setRecallRelocationEnabled(thresholdStats.getRecallRelocationEnabled() + recall);
        } else {
            thresholdStats.setTotalF1ScoreRelocationDisabled(thresholdStats.getTotalF1ScoreRelocationDisabled() + f1Score);
            thresholdStats.setTotalProjectsRelocationDisabled(thresholdStats.getTotalProjectsRelocationDisabled() + 1);
            thresholdStats.setPrecisionRelocationDisabled(thresholdStats.getPrecisionRelocationDisabled() + precision);
            thresholdStats.setRecallRelocationDisabled(thresholdStats.getRecallRelocationDisabled() + recall);
        }
    }
}
