package nl.tudelft.cornul11.thesis.database;

import nl.tudelft.cornul11.thesis.file.JarFileClassMatchInfo;

import java.util.List;

public interface SignatureDao {
    int insertSignature(List<DatabaseManager.Signature> signatures);
    List<JarFileClassMatchInfo> returnMatches(List<String> hashes);
    List<DatabaseManager.Signature> getAllSignatures();
    void closeConnection();
}