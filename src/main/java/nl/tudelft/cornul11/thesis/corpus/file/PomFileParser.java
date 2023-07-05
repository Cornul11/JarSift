package nl.tudelft.cornul11.thesis.corpus.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;

public class PomFileParser {
    private final Logger logger = LoggerFactory.getLogger(PomFileParser.class);

    private final String artifactId;
    private final String version;

    public PomFileParser(InputStream pomInputStream, String name) throws ParserConfigurationException, SAXException, IOException {
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
