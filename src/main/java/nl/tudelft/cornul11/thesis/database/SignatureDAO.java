package nl.tudelft.cornul11.thesis.database;

import nl.tudelft.cornul11.thesis.file.JarInfoExtractor;
import nl.tudelft.cornul11.thesis.file.LibraryMatchInfo;
import nl.tudelft.cornul11.thesis.model.Signature;

import java.util.List;

public interface SignatureDAO {
    int insertLibrary(JarInfoExtractor jarInfoExtractor, long jarHash, long jarCrc);
    int insertSignatures(List<Signature> signatures, long jarHash, long jarCrc);
    List<LibraryMatchInfo> returnTopLibraryMatches(List<Long> hashes);
    void closeConnection();
}