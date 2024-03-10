package nl.tudelft.cornul11.thesis.packaging;


import nl.tudelft.cornul11.thesis.corpus.database.DatabaseConfig;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.model.Dependency;
import nl.tudelft.cornul11.thesis.corpus.model.LibraryInfo;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class UberJarGenerator {
    private static final Logger logger = LoggerFactory.getLogger(UberJarGenerator.class);
    public static final String METADATA_DIRECTORY = "./projects_metadata";
    private SignatureDAO signatureDao;

    public static void main(String[] args) {
        // argument one should be the number of libraries to package

        if (args.length != 2) {
            System.out.println("Usage: java -jar UberJarGenerator.jar <number of uber-jars to create> <max number of libraries per jar>");
            System.exit(1);
        }

        UberJarGenerator uberJarGenerator = new UberJarGenerator();
        try {
            uberJarGenerator.start(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void start(int numSetsOfLibraries, int maxNumLibraries) {
        if (numSetsOfLibraries <= 0 || maxNumLibraries <= 0) {
            throw new IllegalArgumentException("Number of jars and maximum number of libraries must be positive");
        }

        logger.info("Initialization started");
        long startTime = System.currentTimeMillis();

        initDb();
        initPaths();
        LibrarySelector librarySelector = new LibrarySelector();
        ProjectGenerator projectGenerator = new ProjectGenerator(signatureDao);
        MetadataStorage metadataStorage = new MetadataStorage(Path.of(METADATA_DIRECTORY));

        // fetch all libraries once
        List<Dependency> allLibraries = new ArrayList<>();
        Iterator<Dependency> libraryInfoIterator = signatureDao.getAllPossibleLibraries();
        while (libraryInfoIterator.hasNext()) {
            allLibraries.add(libraryInfoIterator.next());
        }

        List<ShadeConfiguration> shadeConfigurations = ShadeConfiguration.getAllConfigurations();
        int totalJars = numSetsOfLibraries * shadeConfigurations.size();
        Random random = new Random();

        logger.info("Starting to generate {} Uber Jars from {} sets of libraries", totalJars, numSetsOfLibraries);
        int jarCount = 0;

        for (int jarNum = 1; jarNum <= numSetsOfLibraries; jarNum++) {
            int randomNumLibraries = random.nextInt(maxNumLibraries) + 1;
            LibraryInfo dependencies = librarySelector.getRandomDependencies(allLibraries, randomNumLibraries);

            for (ShadeConfiguration config : shadeConfigurations) {
                try {
                    ProjectMetadata projectMetadata = projectGenerator.generateProject(dependencies, config);
                    projectMetadata = projectGenerator.packageJar(projectMetadata);
                    metadataStorage.storeMetadata(projectMetadata);

                    jarCount++;
                    logger.info(String.format("Successfully generated Uber Jar %1$d/%2$d. Progress: %3$.2f%%", jarCount, totalJars, ((double) jarCount / totalJars * 100)));
                } catch (Exception e) {
                    logger.error("Error while packaging uber-jar " + jarNum + " with " + randomNumLibraries + " dependencies", e);
                }
            }
        }
        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;
        logger.info("Generation completed in {} seconds. Average time per jar: {} seconds", duration, duration / totalJars);
    }

    private void initPaths() {
        Path metadataDirectory = Path.of(METADATA_DIRECTORY);
        if (!metadataDirectory.toFile().exists()) {
            metadataDirectory.toFile().mkdirs();
        }
    }

    private void initDb() {
        ConfigurationLoader config = new ConfigurationLoader();
        DatabaseManager databaseManager = DatabaseManager.getInstance(config.getDatabaseConfig());
        this.signatureDao = databaseManager.getSignatureDao(config.getDatabaseMode());
    }
}
