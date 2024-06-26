package nl.tudelft.cornul11.thesis.corpus.jarfile;

import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class JarFrequencyAnalyzer {
    private final Logger logger = LoggerFactory.getLogger(JarFrequencyAnalyzer.class);
    private final JarSignatureMapper jarSignatureMapper;

    public JarFrequencyAnalyzer(SignatureDAO signatureDao) {
        this.jarSignatureMapper = new JarSignatureMapper(signatureDao);
    }

    public Map<String, Map<String, Object>> processJar(String path) {
        Map<String, Map<String, Object>> frequencyMap = null;
        Path jarPath = Paths.get(path);
        if (jarPath.toString().endsWith(".jar")) {
            logger.info("Inferring libraries in jar file: " + jarPath);
            frequencyMap = JarSignatureMapper.getTopMatches(jarSignatureMapper.inferJarFile(jarPath));
        }
        return frequencyMap;
    }

    public int getTotalClassCount() {
        return jarSignatureMapper.getTotalClassCount();
    }
}
