<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>${projectName}</artifactId>
    <version>1.0-SNAPSHOT</version>

    <dependencies>
        <#list directDependencies as dependency>
            <dependency>
                <groupId>${dependency.groupId}</groupId>
                <artifactId>${dependency.artifactId}</artifactId>
                <version>${dependency.version}</version>
            </dependency>
        </#list>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <#if shadeConfiguration.relocation && (shadeConfiguration.packagePrefixes?size > 0) >
                        <relocations>
                            <#list shadeConfiguration.packagePrefixes as packagePrefix>
                                <relocation>
                                    <pattern>${packagePrefix}.</pattern>
                                    <shadedPattern>shaded.${packagePrefix}.</shadedPattern>
                                </relocation>
                            </#list>
                        </relocations>
                    </#if>
                    <#if shadeConfiguration.minimizeJar?? >
                        <minimizeJar>${shadeConfiguration.minimizeJar?string("true", "false")}</minimizeJar>
                    </#if>
                    <#if shadeConfiguration.createDependencyReducedPom?? >
                        <createDependencyReducedPom>${shadeConfiguration.createDependencyReducedPom?string("true", "false")}</createDependencyReducedPom>
                    </#if>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>