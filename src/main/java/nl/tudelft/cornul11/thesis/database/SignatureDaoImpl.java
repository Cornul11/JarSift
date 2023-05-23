package nl.tudelft.cornul11.thesis.database;

import nl.tudelft.cornul11.thesis.file.JarFileClassMatchInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SignatureDaoImpl implements SignatureDao {
    private Connection connection;
    private static final Logger logger = LoggerFactory.getLogger(SignatureDaoImpl.class);

    public SignatureDaoImpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void insertSignature(DatabaseManager.Signature signature) {
        String insertQuery = "INSERT INTO signatures (filename, hash, library, version) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insertQuery)) {
            statement.setString(1, signature.fileName());
            statement.setString(2, signature.hash());
            statement.setString(3, signature.library());
            statement.setString(4, signature.version());
            int rowsInserted = statement.executeUpdate();
            logger.info(rowsInserted + " signature row(s) inserted.");
        } catch (SQLException e) {
            // TODO: not sure if this is still needed
            if ("X0Y32".equals(e.getSQLState())) {
//                createSignaturesTable();
                insertSignature(signature); // Retry the insert after creating the table
            } else {
                e.printStackTrace();
            }
        }
    }

    @Override
    public List<JarFileClassMatchInfo> returnMatches(String hash) {
        List<JarFileClassMatchInfo> matches = new ArrayList<>();
        String checkQuery = "SELECT * FROM signatures WHERE hash = ?";
        // run the query to the database
        try (PreparedStatement statement = connection.prepareStatement(checkQuery)) {
            statement.setString(1, hash);
            ResultSet results = statement.executeQuery();

            while (results.next()) {
                String resultFilename = results.getString("filename");
                String resultLibrary = results.getString("library");
                String resultVersion = results.getString("version");

                logger.info("Found match for " + resultFilename + " in library " + resultLibrary + " version " + resultVersion);

                JarFileClassMatchInfo jarFileClassMatchInfo = new JarFileClassMatchInfo(resultFilename, resultLibrary, resultVersion);
                matches.add(jarFileClassMatchInfo);
            }
            return matches;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<DatabaseManager.Signature> getAllSignatures() {
        List<DatabaseManager.Signature> signatureList = new ArrayList<>();
        String selectQuery = "SELECT id, filename, hash, library, version FROM signatures";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(selectQuery)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String fileName = resultSet.getString("filename");
                String hash = resultSet.getString("hash");
                String library = resultSet.getString("library");
                String version = resultSet.getString("version");
                DatabaseManager.Signature signature = new DatabaseManager.Signature(id, fileName, hash, library, version);
                signatureList.add(signature);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return signatureList;
    }

    @Override
    public void closeConnection() {
        try {
            if (connection != null) {
                connection.close();
                logger.info("Database connection closed.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}