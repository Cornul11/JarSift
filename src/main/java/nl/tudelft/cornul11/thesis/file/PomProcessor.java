package nl.tudelft.cornul11.thesis.file;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.*;
import java.io.IOException;
import java.io.InputStream;

public class PomProcessor {
    private String artifactId;
    private String version;
    public PomProcessor(InputStream pomInputStream) throws ParserConfigurationException, SAXException, IOException {
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
