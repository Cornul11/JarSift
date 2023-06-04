package nl.tudelft.cornul11.thesis.database;

import nl.tudelft.cornul11.thesis.file.ClassMatchInfo;

import java.util.List;

public interface SignatureDAO {
    int insertSignatures(List<DatabaseManager.Signature> signatures, String jarHash);

    List<ClassMatchInfo> returnMatches(List<String> hashes);

    List<DatabaseManager.Signature> getAllSignatures();

    void closeConnection();
}