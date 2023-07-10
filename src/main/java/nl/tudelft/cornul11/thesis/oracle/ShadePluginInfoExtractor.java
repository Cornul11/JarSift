package nl.tudelft.cornul11.thesis.oracle;

import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShadePluginInfoExtractor {
    private final Logger logger = LoggerFactory.getLogger(ShadePluginInfoExtractor.class);
    private final SignatureDAO signatureDAO;
    public ShadePluginInfoExtractor(SignatureDAO signatureDAO) {
        this.signatureDAO = signatureDAO;
    }


}
