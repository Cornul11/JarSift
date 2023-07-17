package nl.tudelft.cornul11.thesis.corpus.database;

import nl.tudelft.cornul11.thesis.corpus.file.JarAndPomInfoExtractor;
import nl.tudelft.cornul11.thesis.corpus.file.LibraryMatchInfo;
import nl.tudelft.cornul11.thesis.corpus.model.Signature;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;

import java.util.List;

public interface SignatureDAO {
    int insertLibrary(JarAndPomInfoExtractor jarAndPomInfoExtractor, long jarHash, long jarCrc, boolean isBrokenJar);

    int insertSignatures(List<Signature> signatures, long jarHash, long jarCrc);

    void insertPluginInfo(Model model, Plugin plugin);

    Model retrievePluginInfo(String groupId, String artifactId, String version);

    List<LibraryMatchInfo> returnTopLibraryMatches(List<Long> hashes);

    void closeConnection();
}