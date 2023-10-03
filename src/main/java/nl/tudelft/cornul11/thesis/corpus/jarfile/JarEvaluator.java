package nl.tudelft.cornul11.thesis.corpus.jarfile;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAOImpl;
import nl.tudelft.cornul11.thesis.corpus.model.Dependency;
import nl.tudelft.cornul11.thesis.packaging.ProjectMetadata;
import nl.tudelft.cornul11.thesis.packaging.ShadeConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JarEvaluator {
    double totalF1ScoreMinimizeJarEnabled = 0, totalF1ScoreMinimizeJarDisabled = 0;
    double totalF1ScoreRelocationEnabled = 0, totalF1ScoreRelocationDisabled = 0;
    int totalProjectsMinimizeJarEnabled = 0, totalProjectsMinimizeJarDisabled = 0;
    int totalProjectsRelocationEnabled = 0, totalProjectsRelocationDisabled = 0;
    private final SignatureDAO signatureDao;
    private final String evaluationDirectory;

    public JarEvaluator(SignatureDAO signatureDao, String evaluationDirectory) {
        this.signatureDao = signatureDao;
        this.evaluationDirectory = evaluationDirectory;
    }

    public void evaluate() {
        double totalF1Score = 0;
        int totalProjects = 0;

        JarSignatureMapper jarSignatureMapper = new JarSignatureMapper(signatureDao);

        // TODO: move all hardcoded paths to config or command line arguments
        Path projectsDirectory = Paths.get(evaluationDirectory, "projects");
        File[] projectFolders = projectsDirectory.toFile().listFiles(File::isDirectory);

        if (projectFolders != null) {
            for (File projectFolder : projectFolders) {
                String projectName = projectFolder.getName();
                String jarPath = Paths.get(projectFolder.getAbsolutePath(), "target", projectName + "-1.0-SNAPSHOT.jar").toString();
                String metadataFilePath = Paths.get(evaluationDirectory, "projects_metadata", projectName + ".json").toString();

                if (!Paths.get(jarPath).toFile().exists()) {
                    System.out.println("Skipping " + jarPath + " because it does not exist");
                    continue;
                }

                try {
                    ProjectMetadata groundTruth = fetchGroundTruth(metadataFilePath);
                    List<SignatureDAOImpl.LibraryCandidate> inferredLibraries = jarSignatureMapper.inferJarFile(new FileInputStream(jarPath));

                    Set<String> groundTruthLibrariesSet = new HashSet<>(groundTruth.getDependencies().stream().map(Dependency::getGAV).toList());
                    double f1Score = calculateF1Score(inferredLibraries, groundTruthLibrariesSet);

                    ShadeConfiguration shadeConfig = groundTruth.getShadeConfiguration();
                    if (shadeConfig.isMinimizeJar()) {
                        totalF1ScoreMinimizeJarEnabled += f1Score;
                        totalProjectsMinimizeJarEnabled++;
                    } else {
                        totalF1ScoreMinimizeJarDisabled += f1Score;
                        totalProjectsMinimizeJarDisabled++;
                    }
                    if (shadeConfig.getRelocation()) {
                        totalF1ScoreRelocationEnabled += f1Score;
                        totalProjectsRelocationEnabled++;
                    } else {
                        totalF1ScoreRelocationDisabled += f1Score;
                        totalProjectsRelocationDisabled++;
                    }

                    System.out.printf("F1 Score for %s: %.2f%n", jarPath, f1Score);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            double averageF1ScoreMinimizeJarEnabled = totalF1ScoreMinimizeJarEnabled / totalProjectsMinimizeJarEnabled;
            double averageF1ScoreMinimizeJarDisabled = totalF1ScoreMinimizeJarDisabled / totalProjectsMinimizeJarDisabled;
            double averageF1ScoreRelocationEnabled = totalF1ScoreRelocationEnabled / totalProjectsRelocationEnabled;
            double averageF1ScoreRelocationDisabled = totalF1ScoreRelocationDisabled / totalProjectsRelocationDisabled;

            double totalAverageF1Score =
                    (averageF1ScoreMinimizeJarEnabled +
                            averageF1ScoreMinimizeJarDisabled +
                            averageF1ScoreRelocationEnabled +
                            averageF1ScoreRelocationDisabled) / 4.0;

            System.out.printf("Average F1 Score (minimizeJar enabled): %.2f%n", averageF1ScoreMinimizeJarEnabled);
            System.out.printf("Average F1 Score (minimizeJar disabled): %.2f%n", averageF1ScoreMinimizeJarDisabled);
            System.out.printf("Average F1 Score (relocation enabled): %.2f%n", averageF1ScoreRelocationEnabled);
            System.out.printf("Average F1 Score (relocation disabled): %.2f%n", averageF1ScoreRelocationDisabled);
            System.out.printf("Total Average F1 Score: %.2f%n", totalAverageF1Score);
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

        // f1 score
        return 2 * precision * recall / (precision + recall);
    }

    private ProjectMetadata fetchGroundTruth(String metadataFilePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(Paths.get(metadataFilePath).toFile(), ProjectMetadata.class);
    }
}
