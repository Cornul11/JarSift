package nl.tudelft.cornul11.thesis.packaging;


import nl.tudelft.cornul11.thesis.corpus.database.DatabaseConfig;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.model.LibraryInfo;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;

import java.nio.file.Path;
import java.util.List;

public class UberJarGenerator {
    public static final String METADATA_DIRECTORY = "./projects_metadata";
    private SignatureDAO signatureDao;

    public static void main(String[] args) {
        // argument one should be the number of libraries to package

        if (args.length != 1) {
            System.out.println("Usage: java -jar UberJarGenerator.jar <number of libraries to package>");
            System.exit(1);
        }

        UberJarGenerator uberJarGenerator = new UberJarGenerator();
        try {
            uberJarGenerator.start(Integer.parseInt(args[0]));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void start(int numLibraries) {
        if (numLibraries <= 0) {
            throw new IllegalArgumentException("Number of libraries must be positive");
        }

        initDb();
        initPaths();
        LibrarySelector librarySelector = new LibrarySelector(signatureDao);
        ProjectGenerator projectGenerator = new ProjectGenerator();
        MetadataStorage metadataStorage = new MetadataStorage(Path.of(METADATA_DIRECTORY));

        List<LibraryInfo> libraries = librarySelector.getRandomLibraries(numLibraries);
        for (LibraryInfo library : libraries) {
            try {
                ProjectMetadata projectMetadata = projectGenerator.generateProject(library);

                metadataStorage.storeMetadata(projectMetadata);

                projectGenerator.packageJar(projectMetadata);
            } catch (Exception e) {
                System.err.println("Error while packaging library " + library.getGAV());
                e.printStackTrace();
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
