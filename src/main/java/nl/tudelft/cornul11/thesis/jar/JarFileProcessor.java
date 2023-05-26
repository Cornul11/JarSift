package nl.tudelft.cornul11.thesis.jar;

import nl.tudelft.cornul11.thesis.database.DatabaseManager;
import nl.tudelft.cornul11.thesis.database.SignatureDao;
import nl.tudelft.cornul11.thesis.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.file.JarInfo;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeClass;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeSignatureExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarFileProcessor {
    private final SignatureDao signatureDao;
    private final Logger logger = LoggerFactory.getLogger(JarFileProcessor.class);
    private static HashSet<String> exceptions;

    static {
        exceptions = new HashSet<>();
        exceptions.add("META-INF/"); // META-INF should in general not contain any .class files other than in the versions folder
        // but those are, from what I can see, normally from the same package.
        // TODO: we could of course check whether the classpath of the classes located in /versions/ folder under META-INF/
        // are the same as the classpath of the classes in the root of the JAR file for better precision

        // reference: https://www.logicbig.com/tutorials/core-java-tutorial/java-9-changes/multi-release-jars.html
        exceptions.add("META-INF/versions/");
        exceptions.add("module-info.class");
        exceptions.add("hidden/"); // some kind of weird shading, seen in plexus-utils-1.5.6.jar
        exceptions.add("test/");
    }

    public JarFileProcessor(SignatureDao signatureDao) {
        this.signatureDao = signatureDao;
    }

// TODO: transition to multithreaded operation, process many JARs at once
    public void processJarFile(Path jarFilePath) throws IOException {
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            JarInfo jarInfo = new JarInfo(jarFilePath.toString());

            List<ClassFileInfo> classFileInfos = new ArrayList<>();
            Enumeration<JarEntry> entries = jarFile.entries();
            String initialClassPrefix = null;

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                // TODO: ikasan-uber-spec-3.2.3.jar contains many classes from different JAR files, but does not
                // get flagged as an uber-JAR
                // TODO: maybe, we want to also look at the number of paths at the third level, such as at
                // org.ikasan.spec.* where there are many paths, but only one class per path, each spec representing a different
                // original JAR that was embedded


                // TODO: Home » org.aspectj » aspectjweaver » 1.9.19: this one seems to be skipped, even though the embedded
                // classes are embedded in second level paths, not root level

                // TODO: sisu-inject-bean-1.4.2.jar 
                // TODO: maybe using maven-shade-plugin would instantly be flagged as an uber-JAR, but we need to research this
                //  <plugin>
                //        <artifactId>maven-shade-plugin</artifactId>
                //        <executions>
                //          <execution>
                //            <phase>package</phase>
                //            <goals>
                //              <goal>shade</goal>
                //            </goals>
                //            <configuration>
                //              <artifactSet>
                //                <includes>
                //                  <include>${project.groupId}:${project.artifactId}</include>
                //                </includes>
                //              </artifactSet>
                //              <relocations>
                //                <relocation>
                //                  <pattern>org.objectweb</pattern>
                //                  <shadedPattern>org.sonatype.guice</shadedPattern>
                //                </relocation>
                //              </relocations>
                //              <filters>
                //                <filter>
                //                  <artifact>*:*</artifact>
                //                  <excludes>
                //                    <exclude>org/objectweb/asm/*Adapter*</exclude>
                //                    <exclude>org/objectweb/asm/*Writer*</exclude>
                //                  </excludes>
                //                </filter>
                //              </filters>
                //            </configuration>
                //          </execution>
                //        </executions>
                //      </plugin>
                //      <plugin>
                if (shouldSkip(entry)) {
                    continue;
                }

                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    if (initialClassPrefix == null) {
                        initialClassPrefix = entry.getName().substring(0, entry.getName().indexOf('/') + 1);
                    } else {
                        String classPrefix = entry.getName().substring(0, entry.getName().indexOf('/') + 1);
                        if (!classPrefix.equals(initialClassPrefix)) {
                            logger.warn("JAR file " + jarFilePath + " contains classes from multiple packages, skipping");
                            logger.warn("Initial class prefix: " + initialClassPrefix + ", current class prefix: " + classPrefix);
                            return;
                        }
                    }
                    classFileInfos.add(processClassFile(entry, jarFile));
                }
            }
            commitSignatures(classFileInfos, signatureDao, jarInfo.getGroupId(), jarInfo.getArtifactId(), jarInfo.getVersion());
        }
    }

    private static boolean shouldSkip(JarEntry entry) {
        String name = entry.getName();
        // Skip if the entry is a directory/filename to be ignored
        for (String exception : exceptions) {
            if (name.startsWith(exception)) {
                return true;
            }
        }
        return false;
    }

    private void commitSignatures(List<ClassFileInfo> signatures, SignatureDao signatureDao, String groupID, String artifactID, String version) {
        ArrayList<DatabaseManager.Signature> signaturesToInsert = new ArrayList<>();
        for (ClassFileInfo signature : signatures) {
            // what if the hash is already in the database, but its artifacts are different, or the filename was different
            signaturesToInsert.add(new DatabaseManager.Signature(0, signature.getFileName(), Integer.toString(signature.getHashCode()), groupID, artifactID, version));
        }
        signatureDao.insertSignature(signaturesToInsert);
    }

    private ClassFileInfo processClassFile(JarEntry entry, JarFile jarFile) throws IOException {
        logger.info("Processing class file: " + entry.getName());
        try (InputStream classFileInputStream = jarFile.getInputStream(entry)) {
            byte[] bytecode = classFileInputStream.readAllBytes();
            BytecodeClass bytecodeClass = BytecodeSignatureExtractor.extractSignature(bytecode);
            return new ClassFileInfo(entry.getName(), bytecodeClass.hashCode());
        }
    }
}
