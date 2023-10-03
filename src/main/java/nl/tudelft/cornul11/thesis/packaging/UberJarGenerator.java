package nl.tudelft.cornul11.thesis.packaging;


import nl.tudelft.cornul11.thesis.corpus.database.DatabaseConfig;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.model.Dependency;
import nl.tudelft.cornul11.thesis.corpus.model.LibraryInfo;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class UberJarGenerator {
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

    private void start(int numJars, int maxNumLibraries) {
        if (numJars <= 0 || maxNumLibraries <= 0) {
            throw new IllegalArgumentException("Number of jars and maximum number of libraries must be positive");
        }

        initDb();
        initPaths();
        LibrarySelector librarySelector = new LibrarySelector();
        ProjectGenerator projectGenerator = new ProjectGenerator();
        MetadataStorage metadataStorage = new MetadataStorage(Path.of(METADATA_DIRECTORY));

        // fetch all libraries once
        List<Dependency> allLibraries = new ArrayList<>();
        Iterator<Dependency> libraryInfoIterator = signatureDao.getAllPossibleLibraries();
        while (libraryInfoIterator.hasNext()) {
            allLibraries.add(libraryInfoIterator.next());
        }

        List<ShadeConfiguration> shadeConfigurations = ShadeConfiguration.getAllConfigurations();

        Random random = new Random();

        for (int jarNum = 1; jarNum <= numJars; jarNum++) {
            int randomNumLibraries = random.nextInt(maxNumLibraries) + 1;
            LibraryInfo dependencies = librarySelector.getRandomDependencies(allLibraries, randomNumLibraries);

            for (ShadeConfiguration config : shadeConfigurations) {
                try {
                    ProjectMetadata projectMetadata = projectGenerator.generateProject(dependencies, config);

                    metadataStorage.storeMetadata(projectMetadata);

                    projectGenerator.packageJar(projectMetadata);
                } catch (Exception e) {
                    System.err.println("Error while packaging uber-jar " + jarNum + " with " + randomNumLibraries + " dependencies");
                    e.printStackTrace();
                }
            }
        }
    }

    private void initPaths() {
        Path metadataDirectory = Path.of(METADATA_DIRECTORY);
        if (!metadataDirectory.toFile().exists()) {
            metadataDirectory.toFile().mkdirs();
        }
    }

    private void initDb() {
        DatabaseConfig databaseConfig = new ConfigurationLoader().getDatabaseConfig();
        DatabaseManager databaseManager = DatabaseManager.getInstance(databaseConfig);
        this.signatureDao = databaseManager.getSignatureDao();
    }
}
