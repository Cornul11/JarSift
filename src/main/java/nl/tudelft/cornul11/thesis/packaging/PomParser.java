package nl.tudelft.cornul11.thesis.packaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

public class PomParser {
    private static final Logger logger = LoggerFactory.getLogger(PomParser.class);

    public static String extractVersion(InputStream pomStream, String artifactId) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(pomStream);
            document.getDocumentElement().normalize();

            NodeList artifactIdList = document.getElementsByTagName("artifactId");

            for (int i = 0; i < artifactIdList.getLength(); i++) {
                Node artifactIdNode = artifactIdList.item(i);
                if (artifactId.equals(artifactIdNode.getTextContent())) {
                    Node sibling = artifactIdNode.getNextSibling();
                    while (sibling != null) {
                        if (sibling.getNodeType() == Node.ELEMENT_NODE && "version".equals(sibling.getNodeName())) {
                            return sibling.getTextContent();
                        }
                        sibling = sibling.getNextSibling();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error while parsing pom.xml file", e);
            e.printStackTrace();
        }
        return null;
    }
}
