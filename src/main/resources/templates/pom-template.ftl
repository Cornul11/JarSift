<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>${projectName}</artifactId>
    <version>1.0-SNAPSHOT</version>

    <dependencies>
            <dependency>
                <groupId>${library.groupId}</groupId>
                <artifactId>${library.artifactId}</artifactId>
                <version>${library.version}</version>
            </dependency>
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
<#--                        TODO: disabled for now -->
<#--                        <configuration>-->
<#--                            <relocations>-->
<#--                                <relocation>-->
<#--                                    <pattern>com.example</pattern>-->
<#--                                    <shadedPattern>com.shaded.${relocationParam}</shadedPattern>-->
<#--                                </relocation>-->
<#--                            </relocations>-->
<#--                        </configuration>-->
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>