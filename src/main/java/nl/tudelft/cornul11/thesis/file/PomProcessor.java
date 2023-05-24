package nl.tudelft.cornul11.thesis.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.*;
import java.io.IOException;
import java.io.InputStream;

public class PomProcessor {
    private final Logger logger = LoggerFactory.getLogger(PomProcessor.class);

    private String artifactId;
    private String version;
    public PomProcessor(InputStream pomInputStream, String name) throws ParserConfigurationException, SAXException, IOException {
        logger.info("Processing pom.xml file: " + name);

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

        Document doc = dBuilder.parse(pomInputStream);

        artifactId = doc.getElementsByTagName("artifactId").item(0).getTextContent();
        version = doc.getElementsByTagName("version").item(0).getTextContent();
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }
}
