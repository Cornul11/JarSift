package nl.tudelft.cornul11.thesis.oracle;

import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import org.apache.maven.model.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomWriter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class PomProcessor implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(PomProcessor.class);
    private final Path pomPath;
    private final Map<String, Model> modelCache;
    private final List<String> usingShadePlugin;
    private final AtomicInteger brokenPomCount;
    private final AtomicInteger processedPomCount;
    private final SignatureDAO signatureDao;


    public PomProcessor(Path pomPath, Map<String, Model> modelCache, List<String> usingShadePlugin, AtomicInteger brokenPomCount, AtomicInteger processedPomCount, SignatureDAO signatureDao) {
        this.pomPath = pomPath;
        this.modelCache = modelCache;
        this.usingShadePlugin = usingShadePlugin;
        this.brokenPomCount = brokenPomCount;
        this.processedPomCount = processedPomCount;
        this.signatureDao = signatureDao;
    }

    @Override
    public void run() {
        try {
            boolean containsDependencies = false;
            boolean containsShadeConfig = false;

            Model model = parseModel(pomPath);

            if (model == null) {
                return;
            }

            Properties properties = model.getProperties();

            List<Dependency> dependencies = model.getDependencies();

            if (dependencies.isEmpty()) {
                //System.out.println("No dependencies");
            } else {
                //System.out.println("Dependencies: ");
                containsDependencies = true;
                for (Dependency dependency : dependencies) {
                    String version = dependency.getVersion();

                    if (version == null) {
                        containsDependencies = false;
                        break;
                    }

                    // If version is a property placeholder, replace it with the actual value
                    if (version.startsWith("${") && version.endsWith("}")) {
                        String propertyKey = version.substring(2, version.length() - 1); // Remove ${ and }
                        String propertyValue = properties.getProperty(propertyKey);
                        if (propertyValue != null) {
                            dependency.setVersion(propertyValue);
                        } else {
                            //System.out.println("Warning: property " + propertyKey + " not found");
                        }
                    }

                    // if no version info is available, or there is a range of versions, or there is a variable in the version, it is not usable
                    if (dependency.getVersion() == null || dependency.getVersion().contains("[") || dependency.getVersion().contains("$")) {
                        containsDependencies = false;
                        break;
                    }

                    //System.out.println(dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion());
                }
            }

            Build build = model.getBuild();
            if (build != null) {
                List<Plugin> plugins = build.getPlugins();
                boolean isShadePluginUsed = plugins.stream().anyMatch(plugin -> plugin.getArtifactId().equals("maven-shade-plugin"));
                if (isShadePluginUsed) {
                    usingShadePlugin.add(pomPath.toString());
                    //System.out.println("Shade plugin used");
                    containsShadeConfig = true;
                } else {
                    //System.out.println("Shade plugin not used");
                }
            } else {
                //System.out.println("No build section found in the POM file");
            }
            if (containsDependencies && containsShadeConfig) {
                System.out.println("Will be used for the oracle");

                Plugin shadePlugin = build.getPlugins().
                        stream()
                        .filter(plugin -> plugin.getArtifactId().equals("maven-shade-plugin"))
                        .findFirst()
                        .orElse(null);

                List<String> serializedConfigurations = new ArrayList<>();
                List<PluginExecution> executions = null;
                if (shadePlugin != null) {
                    executions = shadePlugin.getExecutions();
                }

                if (executions != null) {
                    for (PluginExecution execution : executions) {
                        Object configuration = execution.getConfiguration();
                        if (configuration != null) {
                            try {
                                String serializedConfiguration = serializeXpp3Dom((Xpp3Dom) configuration);
                                serializedConfigurations.add(serializedConfiguration);
                            } catch (Exception e) {
                                logger.debug("The error occurred during serialization configuration", e);
                            }
                        } else {
                            logger.debug("The configuration is null, cannot serialize");
                        }
                    }
                }

                signatureDao.insertPluginInfo(model, shadePlugin, serializedConfigurations);
            } else {
                //System.out.println("Will not be used for the oracle");
            }
            System.out.println(pomPath);
            //System.out.println();
        } catch (Exception e) {
            brokenPomCount.incrementAndGet();
            logger.debug("Error while processing POM file: " + pomPath, e);
        } finally {
            int count = processedPomCount.incrementAndGet();
            if (count % 37849 == 0) {
                //System.out.println("Percentage: " + (count * 100.0 / 37849) + "%");
            }
        }
    }

    public String serializeXpp3Dom(Xpp3Dom dom) {
        StringWriter writer = new StringWriter();
        Xpp3DomWriter.write(new PrintWriter(writer), dom);
        return writer.toString();
    }

    private Model parseModel(Path pomPath) throws Exception {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model;
        String key = getKey(pomPath);

        if (modelCache.containsKey(key)) {
            model = modelCache.get(key);
        } else {
            try (Reader fileReader = ReaderFactory.newXmlReader(pomPath.toFile())) {
                model = reader.read(fileReader);
                modelCache.put(key, model);
            }
        }

        Parent parent = model.getParent();
        if (parent != null) {
            if (parent.getGroupId().contains("$") || parent.getArtifactId().contains("$") || parent.getVersion().contains("$")) {
                //System.out.println("Parent POM has variable in it");
                return model;
            }

            //System.out.println("Parent: " + parent.getGroupId() + ":" + parent.getArtifactId() + ":" + parent.getVersion());
            Path parentPomPath = getPomPath(parent);

            // check if parentPomPath exists
            if (!new File(parentPomPath.toString()).exists()) {
                logger.warn("Parent POM file not found: " + parentPomPath);

                return null;
            }

            Model parentModel = parseModel(parentPomPath);

            // update groupId and version if they are not set
            if (model.getGroupId() == null) {
                model.setGroupId(parentModel.getGroupId());
            }
            if (model.getVersion() == null) {
                model.setVersion(parentModel.getVersion());
            }

            // merge dependencies
            List<Dependency> mergedDependencies = new ArrayList<>(parentModel.getDependencies());
            for (Dependency dependency : model.getDependencies()) {
                if (!mergedDependencies.contains(dependency)) {
                    mergedDependencies.add(dependency);
                }
            }
            model.setDependencies(mergedDependencies);

            // Merge plugins
            if (model.getBuild() == null) {
                model.setBuild(new Build());
            }
            if (parentModel.getBuild() != null) {
                List<Plugin> mergedPlugins = new ArrayList<>(parentModel.getBuild().getPlugins());
                for (Plugin plugin : model.getBuild().getPlugins()) {
                    if (!mergedPlugins.contains(plugin)) {
                        mergedPlugins.add(plugin);
                    }
                }
                model.getBuild().setPlugins(mergedPlugins);
            }
        }

        return model;
    }

    @NotNull
    private static String getGroupIdFromParts(String[] parts) {
        int startIdx = 0;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals(".m2") && i + 1 < parts.length && parts[i + 1].equals("repository")) {
                startIdx = i + 2; // start from the part after ".m2/repository"!!
                break;
            }
        }

        // The groupId will be from parts[startIdx] to parts[parts.length - 3], replacing '/' with '.'
        StringBuilder groupIdBuilder = new StringBuilder();
        for (int i = startIdx; i < parts.length - 3; i++) {
            groupIdBuilder.append(parts[i]);
            if (i != parts.length - 4) { // Don't append '.' for the last part
                groupIdBuilder.append('.');
            }
        }
        return groupIdBuilder.toString();
    }

    private String getKey(Path pomPath) {
        return pomPath.toString();
    }

    private Path getPomPath(Parent parent) {
        String parentPomPath = String.join(
                "/",
                parent.getGroupId().replace(".", "/"),
                parent.getArtifactId(),
                parent.getVersion(),
                parent.getArtifactId() + "-" + parent.getVersion() + ".pom"
        );

        String m2RepoPath = pomPath.toString().substring(0, pomPath.toString().lastIndexOf(".m2/repository") + ".m2/repository".length());

        return Paths.get(m2RepoPath, parentPomPath);
    }
}
