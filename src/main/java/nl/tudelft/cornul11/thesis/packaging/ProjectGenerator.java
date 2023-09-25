package nl.tudelft.cornul11.thesis.packaging;

import freemarker.template.Configuration;
import freemarker.template.Template;
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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ProjectGenerator {
    public ProjectMetadata generateProject(LibraryInfo library) throws Exception {
        String projectName = "project" + System.currentTimeMillis();
        Path projectDir = createProjectDirectory(projectName);

        generatePomFile(projectDir, library, projectName);
        generateDummyCode(projectDir, library);

        String relocationParameter = generateRelocationParameter(library);
        return new ProjectMetadata(projectName, library, relocationParameter);
    }

    private String generateRelocationParameter(LibraryInfo library) {
        // TODO: for now this will simply do nothing, and maven-shade-plugin will have no parameters
        return null;
    }

    private Path createProjectDirectory(String projectName) throws IOException {
        Path projectDir = Path.of("./projects/" + projectName);
        Files.createDirectories(projectDir);
        return projectDir;
    }

    private void generatePomFile(Path projectDir, LibraryInfo library, String projectName) throws IOException {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassLoaderForTemplateLoading(this.getClass().getClassLoader(), "templates");

        Template template = cfg.getTemplate("pom-template.ftl");

        Map<String, Object> input = new HashMap<>();
        input.put("projectName", projectName);
        input.put("library", library);
//        input.put("relocationParam", generateRandomString()); TODO: disabled for now

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

    private void generateDummyCode(Path projectDir, LibraryInfo library) throws Exception {
        String className = getClassName(library);
        if (className == null || className.isBlank()) {
            throw new Exception("Could not get class name from library");
        }

        String dummyClassName = "Dummy" + getSimpleClassName(className);

        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassLoaderForTemplateLoading(this.getClass().getClassLoader(), "templates");

        Template template = cfg.getTemplate("dummy-class-template.ftl");

        Map<String, Object> input = new HashMap<>();
        input.put("dummyClassName", dummyClassName);
        input.put("className", className);

        Path srcDir = projectDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        try (Writer fileWriter = Files.newBufferedWriter(srcDir.resolve(dummyClassName + ".java"))) {
            template.process(input, fileWriter);
        }
    }

    private String getClassName(LibraryInfo library) {
        String jarLocation = getJarLocation(library);
        try (JarFile jarFile = new JarFile(jarLocation)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                // TODO: filter out special classes (e.g. module-info.class, package-info.class, META-INF/*)
                if (name.endsWith(".class") && !name.contains("$")) {
                    ClassReader classReader = new ClassReader(jarFile.getInputStream(entry));
                    ClassNode classNode = new ClassNode();
                    classReader.accept(classNode, 0);

                    if ((classNode.access & Opcodes.ACC_ABSTRACT) == 0 &&
                    (classNode.access & Opcodes.ACC_PUBLIC) != 0) {
                        // check if there is an available public default constructor
                        for (MethodNode method : classNode.methods) {
                            if ("<init>".equals(method.name) &&
                            "()V".equals(method.desc) &&
                                    (method.access & Opcodes.ACC_PUBLIC) != 0) {
                                return name.replace("/", ".").replace(".class", "");
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getJarLocation(LibraryInfo library) {
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

    public void packageJar(ProjectMetadata projectMetadata) {
        // Run Maven command to package project into a jar.
        // maybe use the Java Maven lib instead of running a command in the terminal
        // "mvn clean package" command can be run programmatically.
        Invoker invoker = new DefaultInvoker();
        InvocationRequest request = new DefaultInvocationRequest();

        // TODO: on the server, the maven home is different, so this will have to be accounted for

        String projectName = projectMetadata.getProjectName();
        String filePath = Paths.get("projects", projectName, "pom.xml").toString();

        request.setPomFile(new File(filePath));
        request.setGoals(Collections.singletonList("clean package"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        invoker.setOutputHandler(new PrintStreamHandler(new PrintStream(baos), true));

        try {
            InvocationResult result = invoker.execute(request);
            if (result.getExitCode() != 0) {
                System.err.println("Maven command output: " + baos);
                throw new RuntimeException("Maven command failed");
            }
        } catch (MavenInvocationException e) {
            e.printStackTrace();
            System.err.println("Maven command output: " + baos);
        }

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
    }
}
