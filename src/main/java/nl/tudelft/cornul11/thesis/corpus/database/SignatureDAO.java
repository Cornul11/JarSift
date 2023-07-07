package nl.tudelft.cornul11.thesis.corpus.database;

import nl.tudelft.cornul11.thesis.corpus.file.JarInfoExtractor;
import nl.tudelft.cornul11.thesis.corpus.file.LibraryMatchInfo;
import nl.tudelft.cornul11.thesis.corpus.model.Signature;
import org.apache.maven.model.Model;

import java.util.List;

public interface SignatureDAO {
    int insertLibrary(JarInfoExtractor jarInfoExtractor, long jarHash, long jarCrc, boolean isBrokenJar);
    int insertSignatures(List<Signature> signatures, long jarHash, long jarCrc);
    void insertPluginInfo(Model model);
    List<LibraryMatchInfo> returnTopLibraryMatches(List<Long> hashes);
    void closeConnection();
}