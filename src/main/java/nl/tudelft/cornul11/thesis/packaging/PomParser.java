package nl.tudelft.cornul11.thesis.packaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class PomParser {
    private static final Logger logger = LoggerFactory.getLogger(PomParser.class);

    public static String extractVersion(InputStream pomStream, String artifactId) throws IOException, SAXException, ParserConfigurationException {
        String content = inputStreamToString(pomStream);

        // replace problematic characters
        content = content.replace("�", "?");

        InputStream correctedStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(correctedStream);
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
        return null;
    }

    private static String inputStreamToString(InputStream is) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }
}
