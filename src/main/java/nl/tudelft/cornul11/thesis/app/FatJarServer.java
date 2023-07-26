package nl.tudelft.cornul11.thesis.app;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseConfig;
import nl.tudelft.cornul11.thesis.corpus.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAOImpl.LibraryCandidate;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarSignatureMapper;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.resource.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Jetty server
 */
public class FatJarServer extends AbstractHandler {
    Path multipartTmpDir = Paths.get("target", "multipart-tmp");
    String location = multipartTmpDir.toString();
    long maxFileSize = 100000 * 1024 * 1024; // 1 GB
    long maxRequestSize = 100000 * 1024 * 1024; // 10 MB
    int fileSizeThreshold = 64 * 1024; // 64 KB

    ConfigurationLoader config = new ConfigurationLoader();
    DatabaseConfig databaseConfig = config.getDatabaseConfig();
    DatabaseManager databaseManager = DatabaseManager.getInstance(databaseConfig);
    SignatureDAO signatureDao = databaseManager.getSignatureDao();
    JarSignatureMapper jarSignatureMapper = new JarSignatureMapper(signatureDao);

    MultipartConfigElement multipartConfig = new MultipartConfigElement(location, maxFileSize, maxRequestSize,
            fileSizeThreshold);

    public FatJarServer() {
        super();
    }

    private void handleUpload(HttpServletRequest request, HttpServletResponse response, Path outputDir)
            throws ServletException, IOException {
        response.setCharacterEncoding("utf-8");
        response.setContentType("application/json");

        double threasold = 0.85;
        if (request.getParameter("threshold") != null) {
            threasold = Double.parseDouble(request.getParameter("threshold"));
        }

        for (Part part : request.getParts()) {
            String filename = part.getSubmittedFileName();
            if (StringUtil.isNotBlank(filename) && filename.endsWith(".jar")) {
                try (InputStream inputStream = part.getInputStream()) {

                    // String fileName = part.getSubmittedFileName();

                    List<LibraryCandidate> inferJarFile = jarSignatureMapper.inferJarFile(inputStream);

                    // Sort in decreasing order of count
                    inferJarFile.sort((data1, data2) -> Double.compare(data2.getIncludedRatio(),
                            data1.getIncludedRatio()));

                    response.getWriter().append("[");
                    boolean isFirst = true;
                    for (LibraryCandidate lib : inferJarFile) {
                        if (lib.getIncludedRatio() < threasold) {
                            continue;
                        }
                        if (!isFirst) {
                            response.getWriter().append(",");
                        } else {
                            isFirst = false;
                        }
                        response.getWriter().append(lib.toJSON());
                    }
                    response.getWriter().append("]");
                }
            }
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().flush();
    }

    @Override
    public void handle(String target, Request jettyRequest, HttpServletRequest request,
            HttpServletResponse response) {
        // get file upload from request
        if (target.equals("/upload")) {
            jettyRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, multipartConfig);
            try {
                handleUpload(jettyRequest, response, Path.of("uploads"));
            } catch (ServletException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void run() throws Exception {
        int port = System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 8080;
        // Create a Server instance.
        Server server = new Server(port);

        // Create a ServerConnector to accept connections from clients.
        Connector connector = new ServerConnector(server);

        // Add the Connector to the Server
        server.addConnector(connector);

        // Create a ContextHandlerCollection to hold contexts.
        ContextHandlerCollection contextCollection = new ContextHandlerCollection();
        // Link the ContextHandlerCollection to the Server.
        server.setHandler(contextCollection);

        // Create and configure a ResourceHandler.
        ResourceHandler ressourceHandler = new ResourceHandler();
        // Configure the directory where static resources are located.
        ressourceHandler.setBaseResource(Resource.newResource("www/"));
        // Configure directory listing.
        ressourceHandler.setDirectoriesListed(true);
        // Configure welcome files.
        ressourceHandler.setWelcomeFiles(new String[] { "index.html" });
        // Configure whether to accept range requests.
        ressourceHandler.setAcceptRanges(true);

        ContextHandler apiContext = new ContextHandler();
        apiContext.setContextPath("/api");
        apiContext.setHandler(new FatJarServer());
        contextCollection.addHandler(apiContext);

        ContextHandler publicContext = new ContextHandler();
        publicContext.setContextPath("/");
        publicContext.setHandler(ressourceHandler);
        contextCollection.addHandler(publicContext);

        // Start the Server so it starts accepting connections from clients.
        server.start();
        server.join();
    }

}
