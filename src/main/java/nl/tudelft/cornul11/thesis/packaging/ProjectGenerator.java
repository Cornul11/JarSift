package nl.tudelft.cornul11.thesis.packaging;

import freemarker.template.Configuration;
import freemarker.template.Template;
import nl.tudelft.cornul11.thesis.corpus.jarfile.JarProcessingUtils;
import nl.tudelft.cornul11.thesis.corpus.model.Dependency;
import nl.tudelft.cornul11.thesis.corpus.model.LibraryInfo;
import org.apache.maven.shared.invoker.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ProjectGenerator {
    public ProjectMetadata generateProject(LibraryInfo dependencies, ShadeConfiguration shadeConfiguration) throws Exception {
        String projectName = "project" + System.currentTimeMillis();
        Path projectDir = createProjectDirectory(projectName);

        generateDummyCode(projectDir, dependencies.getDependencies(), shadeConfiguration);
        generatePomFile(projectDir, dependencies.getDependencies(), shadeConfiguration, projectName);

        return new ProjectMetadata(projectName, dependencies.getDependencies(), shadeConfiguration);
    }

    private Path createProjectDirectory(String projectName) throws IOException {
        Path projectDir = Path.of("./projects/" + projectName);
        Files.createDirectories(projectDir);
        return projectDir;
    }

    private void generatePomFile(Path projectDir, List<Dependency> directDependencies, ShadeConfiguration shadeConfiguration, String projectName) throws IOException {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassLoaderForTemplateLoading(this.getClass().getClassLoader(), "templates");

        Template template = cfg.getTemplate("pom-template.ftl");

        Map<String, Object> input = new HashMap<>();
        input.put("projectName", projectName);
        input.put("directDependencies", directDependencies);
        input.put("shadeConfiguration", shadeConfiguration);

        try (Writer fileWriter = Files.newBufferedWriter(projectDir.resolve("pom.xml"))) {
            template.process(input, fileWriter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getSimpleClassName(String fullName) {
        int lastDotIndex = fullName.lastIndexOf('.');
        return lastDotIndex != -1 ? fullName.substring(lastDotIndex + 1) : fullName;
    }

    private void generateDummyCode(Path projectDir, List<Dependency> dependencies, ShadeConfiguration shadeConfiguration) throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassLoaderForTemplateLoading(this.getClass().getClassLoader(), "templates");

        Template template = cfg.getTemplate("dummy-class-template.ftl");

        Path srcDir = projectDir.resolve("src/main/java/com/example/dummy");
        Files.createDirectories(srcDir);

        Set<String> packagePrefixes = new HashSet<>();

        for (Dependency dependency : dependencies) {
            Result result = getClassName(dependency);
            String className = result.className;
            if (className == null || className.isBlank()) {
                System.err.println("Skipping dependency: " + dependency.getGAV() + " has no valid class found");
                continue;
            }

            String dummyClassName = "Dummy" + getSimpleClassName(className);

            Map<String, Object> input = new HashMap<>();
            input.put("dummyClassName", dummyClassName);
            input.put("className", className);
            try (Writer fileWriter = Files.newBufferedWriter(srcDir.resolve(dummyClassName + ".java"))) {
                template.process(input, fileWriter);
            }

            packagePrefixes.addAll(result.packagePrefixes);
        }

        shadeConfiguration.setPackagePrefixes(new ArrayList<>(packagePrefixes));
    }

    private String getPackagePrefix(String className) {
        int firstDotIndex = className.indexOf('.');
        if (firstDotIndex != -1) {
            return className.substring(0, firstDotIndex);
        }
        return null;
    }

    private Result getClassName(Dependency library) {
        String jarLocation = getJarLocation(library);
        try (JarFile jarFile = new JarFile(jarLocation)) {
            return processJarFile(jarFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Result(null, Collections.emptySet());
    }

    private Result processJarFile(JarFile jarFile) {
        Set<String> packagePrefixes = new HashSet<>();
        String className = null;

        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!JarProcessingUtils.shouldSkip(entry)) {
                if (JarProcessingUtils.isClassFile(entry, entry.getName()) &&
                        !JarProcessingUtils.isInnerClassFile(entry.getName())) {
                    // add package prefix to set
                    String classFullName = name.replace("/", ".").replace(".class", "");
                    String packagePrefix = getPackagePrefix(classFullName);
                    if (packagePrefix != null) {
                        packagePrefixes.add(packagePrefix);
                    }

                    if (className == null) {
                        className = getValidClassName(jarFile, entry);
                    }
                }
            }
        }
        return new Result(className, packagePrefixes);
    }


    private String getValidClassName(JarFile jarFile, JarEntry entry) {
        try {
            ClassReader classReader = new ClassReader(jarFile.getInputStream(entry));
            ClassNode classNode = new ClassNode();
            classReader.accept(classNode, 0);

            if (isPublicNonAbstractClass(classNode) && hasPublicDefaultConstructor(classNode)) {
                return entry.getName().replace("/", ".").replace(".class", "");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean isPublicNonAbstractClass(ClassNode classNode) {
        return (classNode.access & Opcodes.ACC_PUBLIC) != 0
                && (classNode.access & Opcodes.ACC_ABSTRACT) == 0;
    }

    private boolean hasPublicDefaultConstructor(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if ("<init>".equals(method.name) &&
                    "()V".equals(method.desc) &&
                    (method.access & Opcodes.ACC_PUBLIC) != 0) {
                return true;
            }
        }
        return false;
    }


    private String getJarLocation(Dependency library) {
        String userHomeDir = System.getProperty("user.home");
        Path m2RepositoryPath = Paths.get(userHomeDir, ".m2", "repository");

        String groupId = library.getGroupId();
        String artifactId = library.getArtifactId();
        String version = library.getVersion();

        // get library jar file
        Path jarLocation = Paths.get(m2RepositoryPath.toString(),
                groupId.replace(".", "/"),
                artifactId,
                version,
                artifactId + "-" + version + ".jar");

        return jarLocation.toString();
    }

    public ProjectMetadata packageJar(ProjectMetadata projectMetadata) {
        Invoker invoker = new DefaultInvoker();
        InvocationRequest request = createInvocationRequest(projectMetadata);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        executeMavenGoal(request, invoker, "clean package", baos);

        List<Dependency> effectiveDependencies = getDependencies(request, invoker);
        projectMetadata = projectMetadata.withEffectiveDependencies(effectiveDependencies);

        // if we got here, then the Jar was successfully generated
        boolean moveJar = false;
        if (moveJar) {
            Path source = Paths.get(projectMetadata.getProjectName(), "target", projectMetadata.getProjectName() + "-1.0-SNAPSHOT.jar");
            Path destination = Paths.get(/*DATASET_DIRECTORY,*/ projectMetadata.getProjectName() + ".jar"); // TODO: define DATASET_DIRECTORY
            try {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return projectMetadata; // with the newly added effective dependencies
    }

    private List<Dependency> getDependencies(InvocationRequest request, Invoker invoker) {
        List<Dependency> dependencies = new ArrayList<>();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        executeMavenGoal(request, invoker, "dependency:list", baos);

        String[] lines = baos.toString().split("\n");
        for (String line : lines) {
            if (line.contains(":jar:") && line.contains(":compile")) {
                line = line.replace("[INFO]", "").trim();

                String[] parts = line.split(":");

                if (parts.length >= 5) {
                    String groupId = parts[0];
                    String artifactId = parts[1];
                    String version = parts[3];
                    dependencies.add(new Dependency(groupId, artifactId, version));
                }
            }
        }
        return dependencies;
    }

    private void executeMavenGoal(InvocationRequest request, Invoker invoker, String goal, ByteArrayOutputStream baos) {
        request.setGoals(Collections.singletonList(goal));
        request.setOutputHandler(new PrintStreamHandler(new PrintStream(baos), true));

        try {
            InvocationResult result = invoker.execute(request);
            if (result.getExitCode() != 0) {
                System.err.println("Maven command output for " + goal + ": " + baos);
                throw new RuntimeException("Maven command for " + goal + " failed");
            }
        } catch (MavenInvocationException e) {
            e.printStackTrace();
            System.err.println("Maven command output for " + goal + ": " + baos);
        }
    }

    private InvocationRequest createInvocationRequest(ProjectMetadata metadata) {
        InvocationRequest request = new DefaultInvocationRequest();
        String projectName = metadata.getProjectName();

        // TODO: on the server, the maven home is different, so this will have to be accounted for
        String filePath = Paths.get("projects", projectName, "pom.xml").toString();
        request.setPomFile(new File(filePath));
        return request;
    }

    private static class Result {
        final String className;
        final Set<String> packagePrefixes;

        Result(String className, Set<String> packagePrefixes) {
            this.className = className;
            this.packagePrefixes = packagePrefixes;
        }
    }
}
