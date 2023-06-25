package nl.tudelft.cornul11.thesis.database;

import nl.tudelft.cornul11.thesis.file.ClassMatchInfo;
import nl.tudelft.cornul11.thesis.file.LibraryMatchInfo;

import java.util.List;

public interface SignatureDAO {
    int insertSignatures(List<DatabaseManager.Signature> signatures, String jarHash);
    List<LibraryMatchInfo> returnTopLibraryMatches(List<String> hashes);
    void closeConnection();
}