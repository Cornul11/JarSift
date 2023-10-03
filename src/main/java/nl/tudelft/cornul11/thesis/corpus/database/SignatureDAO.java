package nl.tudelft.cornul11.thesis.corpus.database;

import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAOImpl.LibraryCandidate;
import nl.tudelft.cornul11.thesis.corpus.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.corpus.file.JarAndPomInfoExtractor;
import nl.tudelft.cornul11.thesis.corpus.model.Dependency;
import nl.tudelft.cornul11.thesis.corpus.model.Signature;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;

import java.util.Iterator;
import java.util.List;

public interface SignatureDAO {
    int insertLibrary(JarAndPomInfoExtractor jarAndPomInfoExtractor, long jarHash, long jarCrc, boolean isBrokenJar);

    int insertSignatures(List<Signature> signatures, long jarHash, long jarCrc);

    void insertPluginInfo(Model model, Plugin shadePlugin, boolean minimizeJar, boolean usingMavenShade, boolean isUberJar);

    Model retrievePluginInfo(String groupId, String artifactId, String version);

    List<SignatureDAOImpl.OracleLibrary> getOracleLibraries();

    List<LibraryCandidate> returnTopLibraryMatches(List<ClassFileInfo> signatures);

    void closeConnection();

    boolean isLibraryInDB(String library);

    Iterator<Dependency> getAllPossibleLibraries();
}