package nl.tudelft.cornul11.thesis.oracle;

import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import org.apache.maven.model.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class PomProcessor implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(PomProcessor.class);
    private final Path pomPath;
    private final Map<String, Model> modelCache;
    private final AtomicInteger usingShadePlugin;
    private final AtomicInteger brokenPomCount;
    private final AtomicInteger insertedPomCount;
    private final SignatureDAO signatureDao;


    public PomProcessor(Path pomPath, Map<String, Model> modelCache, AtomicInteger usingShadePlugin, AtomicInteger brokenPomCount, AtomicInteger processedPomCount, SignatureDAO signatureDao) {
        this.pomPath = pomPath;
        this.modelCache = modelCache;
        this.usingShadePlugin = usingShadePlugin;
        this.brokenPomCount = brokenPomCount;
        this.insertedPomCount = processedPomCount;
        this.signatureDao = signatureDao;
    }

    @Override
    public void run() {
        try {
            if (pomPath.toString().contains("drill-jdbc-all")) {
                logger.info("Processing pom: " + pomPath);
            }
            Model model = parseModel(pomPath);

            // if the model could not be resolved or not verified, we do not want to proceed
            if (model == null) {
                return;
            }

            Build build = model.getBuild();
            if (build != null) {
                incrementShadePluginCount(build);
            }

            if (!verifyModel(model)) {
                return;
            }

            if (build != null) {
                List<Plugin> plugins = build.getPlugins();
                Optional<Plugin> shadePluginOptional = plugins.stream()
                        .filter(plugin -> plugin.getArtifactId().equals("maven-shade-plugin"))
                        .findFirst();

                if (shadePluginOptional.isPresent()) {
                    boolean usingShadePlugin = true;
                    Plugin shadePlugin = shadePluginOptional.get();

                    if (!isConfigMultiplicityAcceptable(shadePlugin)) {
                        logger.debug("More than one config present in POM: " + pomPath);
                        return;
                    }

                    Xpp3Dom pluginConfiguration = (Xpp3Dom) shadePlugin.getConfiguration();
                    boolean isMinimizeJarPresent = isMinimizeJarConfigPresent(pluginConfiguration);

                    if (pluginConfiguration != null) {
                        signatureDao.insertPluginInfo(model, shadePlugin, isMinimizeJarPresent, usingShadePlugin, true);
                        insertedPomCount.incrementAndGet();
                        if (isMinimizeJarPresent) {
                            logger.debug("Minimize jar config present in POM: " + pomPath);
                        }
                    }

                    for (PluginExecution execution : shadePlugin.getExecutions()) {
                        Xpp3Dom executionConfiguration = (Xpp3Dom) execution.getConfiguration();
                        isMinimizeJarPresent = isMinimizeJarConfigPresent(executionConfiguration);
                        if (executionConfiguration != null) {
                            signatureDao.insertPluginInfo(model, shadePlugin, isMinimizeJarPresent, usingShadePlugin, true);
                            insertedPomCount.incrementAndGet();
                            if (isMinimizeJarPresent) {
                                logger.debug("Minimize jar config present in execution of POM: " + pomPath);
                            }
                        }
                    }
                } else {
                    // check if maven-assembly-plugin is present
                    Optional<Plugin> assemblyPluginOptional = plugins.stream()
                            .filter(plugin -> plugin.getArtifactId().equals("maven-assembly-plugin"))
                            .findFirst();

                    // if both maven-shade-plugin and maven-assembly-plugin are not present,
                    // we can most likely assume that this is not an uber jar
                    if (assemblyPluginOptional.isEmpty()) {
                        signatureDao.insertPluginInfo(model, null, false, false, false);
                        insertedPomCount.incrementAndGet();
                    }
                }
            }
        } catch (Exception e) {
            brokenPomCount.incrementAndGet();
            logger.debug("Error while processing POM file: " + pomPath, e);
        }
    }

    private boolean isMinimizeJarConfigPresent(Xpp3Dom config) {
        Xpp3Dom minimizeJarNode = config.getChild("minimizeJar");
        return minimizeJarNode != null && Boolean.parseBoolean(minimizeJarNode.getValue());
    }

    private long countNonNullConfigurations(Plugin plugin) {
        Stream<Xpp3Dom> allConfigs = Stream.concat(
                Stream.ofNullable((Xpp3Dom) plugin.getConfiguration()),
                plugin.getExecutions().stream().map(execution -> (Xpp3Dom) execution.getConfiguration())
        );
        return allConfigs
                .filter(Objects::nonNull)
                .count();
    }

    private boolean isConfigMultiplicityAcceptable(Plugin plugin) {
        long count = countNonNullConfigurations(plugin);
        return count <= 1;
    }

    private void incrementShadePluginCount(Build build) {
        List<Plugin> plugins = build.getPlugins();
        Optional<Plugin> shadePluginOptional = plugins.stream()
                .filter(plugin -> plugin.getArtifactId().equals("maven-shade-plugin"))
                .findFirst();
        if (shadePluginOptional.isPresent()) {
            usingShadePlugin.getAndIncrement();
        }
    }

    private boolean isExecutionConfigPresent(List<PluginExecution> executions) {
        return executions.stream()
                .anyMatch(execution -> execution.getConfiguration() != null);
    }

    private boolean verifyModel(Model model) {
        Properties properties = model.getProperties();
        List<Dependency> dependencies = model.getDependencies();

        // check if all dependencies are resolved
        for (Dependency dependency : dependencies) {
            String version = dependency.getVersion();
            if (version == null || version.contains("[")) {
                return false;
            }

            // if version is a property placeholder, attempt to resolve it
            if (version.startsWith("${") && version.endsWith("}")) {
                if (dependency.getScope() != null && dependency.getScope().equals("test")) {
                    // we do not care about the version resolution of test dependencies (they are not shaded)
                    continue;
                }

                String propertyKey = version.substring(2, version.length() - 1);
                String propertyValue = properties.getProperty(propertyKey);
                if (propertyValue == null) {
                    return false;
                } else {
                    dependency.setVersion(propertyValue);
                }
            }
        }

        // check parent
        Parent parent = model.getParent();
        if (parent != null) {
            parent = resolveParentProperties(parent, model);
            Path parentPomPath = getPomPath(parent, model);
            if (!verifyParent(parent, parentPomPath)) {
                logger.warn("Parent POM file not found: " + parentPomPath);
                return false;
            }
        }

        return true;
    }

    private Parent resolveParentProperties(Parent parent, Model model) {
        String groupId = resolveProperty(parent.getGroupId(), model);
        String artifactId = resolveProperty(parent.getArtifactId(), model);
        String version = resolveProperty(parent.getVersion(), model);
        parent.setGroupId(groupId);
        parent.setArtifactId(artifactId);
        parent.setVersion(version);
        return parent;
    }

    private String resolveProperty(String propertyValue, Model model) {
        if (propertyValue == null) {
            return null;
        }

        if (propertyValue.startsWith("${") && propertyValue.endsWith("}")) {
            String propertyKey = propertyValue.substring(2, propertyValue.length() - 1);
            Properties properties = model.getProperties();

            if ("project.version".equals(propertyKey)) {
                return model.getVersion();
            }

            String resolvedValue = properties.getProperty(propertyKey);
            if (resolvedValue != null) {
                propertyValue = resolvedValue;
            } else {
                Parent parent = model.getParent();
                if (parent != null) {
                    Path parentPomPath = getPomPath(parent, model);
                    try {
                        Model parentModel = parseModel(parentPomPath);
                        propertyValue = resolveProperty(propertyValue, parentModel);
                    } catch (Exception e) {
                        logger.debug("Error while processing parent POM file: " + parentPomPath, e);
                    }
                }
            }
        }
        return propertyValue;
    }

    private boolean verifyParent(Parent parent, Path parentPomPath) {
        if (parent.getGroupId().contains("$") ||
                parent.getArtifactId().contains("$") ||
                parent.getVersion().contains("$")) {
            return false;
        }

        return new File(parentPomPath.toString()).exists();
    }

    public static String serializeXpp3Dom(Xpp3Dom dom) {
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
            // check if pomPath exists
            if (!new File(pomPath.toString()).exists()) {
                logger.warn("POM file not found: " + pomPath);
                return null;
            }
            try (Reader fileReader = ReaderFactory.newXmlReader(pomPath.toFile())) {
                model = reader.read(fileReader);
                modelCache.put(key, model);
            } catch (Exception e) {
                logger.warn("Error while parsing POM file: " + pomPath, e);
                return null;
            }
        }

        Parent parent = model.getParent();
        if (parent != null) {
            Path parentPomPath = getPomPath(parent, model);

            // check if parentPomPath exists
            if (!new File(parentPomPath.toString()).exists()) {
                logger.warn("Parent POM file not found: " + parentPomPath);
                return null;
            }

            Model parentModel = parseModel(parentPomPath);

            if (parentModel == null || !verifyModel(parentModel)) {
                return null;
            }

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

            model.getDependencies().forEach(dependency -> {
                String version = resolveProperty(dependency.getVersion(), model);
                dependency.setVersion(version);
            });

            if (!verifyModel(model)) {
                return null;
            }

            // Merge plugins
            if (model.getBuild() == null) {
                model.setBuild(new Build());
            }
            if (parentModel.getBuild() != null) {
                List<Plugin> mergedPlugins = new ArrayList<>(parentModel.getBuild().getPlugins());
                for (Plugin plugin : model.getBuild().getPlugins()) {
                    Optional<Plugin> parentPluginOptional = mergedPlugins.stream()
                            .filter(p -> p.getArtifactId().equals(plugin.getArtifactId()))
                            .findFirst();

                    if (parentPluginOptional.isPresent()) {
                        Plugin parentPlugin = parentPluginOptional.get();

                        // if the child pom does not specify the plugin configuration, use the parent's
                        if (plugin.getConfiguration() == null) {
                            plugin.setConfiguration(parentPlugin.getConfiguration());
                        }

                        List<PluginExecution> mergedExecutions = new ArrayList<>(parentPlugin.getExecutions());
                        for (PluginExecution execution : plugin.getExecutions()) {
                            if (!mergedExecutions.contains(execution)) {
                                mergedExecutions.add(execution);
                            }
                        }
                        plugin.setExecutions(mergedExecutions);
                    }

                    if (!mergedPlugins.contains(plugin)) {
                        mergedPlugins.add(plugin);
                    }
                }
                model.getBuild().setPlugins(mergedPlugins);
            }
        }

        resolveImportedDependencies(model);

        return model;
    }

    private void resolveImportedDependencies(Model model) throws Exception {
        DependencyManagement depManagement = model.getDependencyManagement();
        if (depManagement != null) {
            List<Dependency> addDependencies = new ArrayList<>();
            Iterator<Dependency> dependencyIterator = depManagement.getDependencies().iterator();
            while (dependencyIterator.hasNext()) {
                Dependency depManagementDependency = dependencyIterator.next();
                if (depManagementDependency.getScope() != null && depManagementDependency.getScope().equals("import")) {
                    Path importedPomPath = getPomPath(depManagementDependency.getGroupId(), depManagementDependency.getArtifactId(), depManagementDependency.getVersion(), model);
                    Model importedModel = parseModel(importedPomPath);
                    if (importedModel != null && importedModel.getDependencyManagement() != null) {
                        List<Dependency> importedDependencies = importedModel.getDependencyManagement().getDependencies();
                        importedDependencies.forEach(dependency -> {
                            String version = resolveProperty(dependency.getVersion(), importedModel);
                            dependency.setVersion(version);
                        });
                        addDependencies.addAll(importedDependencies);
                    }
                    dependencyIterator.remove();
                }
            }
            depManagement.getDependencies().addAll(addDependencies);

            for (Dependency addDependency : addDependencies) {
                if (!model.getDependencies().contains(addDependency)) {
                    model.getDependencies().add(addDependency);
                }
            }
        }
    }

    private String getKey(Path pomPath) {
        return pomPath.toString();
    }

    private Path getPomPath(Parent parent, Model model) {
        String groupId = resolveProperty(parent.getGroupId(), model);
        String artifactId = resolveProperty(parent.getArtifactId(), model);
        String version = resolveProperty(parent.getVersion(), model);


        String parentPomPath = String.join(
                "/",
                groupId.replace(".", "/"),
                artifactId,
                version,
                artifactId + "-" + version + ".pom"
        );

        String m2RepoPath = pomPath.toString().substring(0, pomPath.toString().lastIndexOf(".m2/repository") + ".m2/repository".length());

        return Paths.get(m2RepoPath, parentPomPath);
    }

    private Path getPomPath(String groupId, String artifactId, String version, Model model) {
        groupId = resolveProperty(groupId, model);
        artifactId = resolveProperty(artifactId, model);
        version = resolveProperty(version, model);

        String parentPomPath = String.join(
                "/",
                groupId.replace(".", "/"),
                artifactId,
                version,
                artifactId + "-" + version + ".pom"
        );


        String m2RepoPath = pomPath.toString().substring(0, pomPath.toString().lastIndexOf(".m2/repository") + ".m2/repository".length());

        return Paths.get(m2RepoPath, parentPomPath);
    }
}
