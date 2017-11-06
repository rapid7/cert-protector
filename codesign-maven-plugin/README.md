codesign-maven-plugin
=====================

This is a simple maven plugin to sign files by uploading to the cert-protector service.

## Getting Started

To use this plugin and start signing files, declare the plugin and add a dependency on
codesign-maven-plugin:

```
....
<build>
  <plugins>
    <plugin>
      <groupId>com.rapid7</groupId>
      <artifactId>codesign-maven-plugin</artifactId>
      <version>0.1.0</version>
      <executions>
        <execution>
          <id>sign-installer</id>
          <phase>package</phase>
          <goals>
            <goal>codesign</goal>
          </goals>
          <configuration>
            <username>${codesign.username}</username>
            <password>${codesign.password}</password>
            <fileset>
              <directory>${project.build.directory}/media</directory>
              <includes>
                <include>*.exe</include>
              </includes>
            </fileset>
            <outputDir>${project.build.directory}/media</outputDir>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
....
<dependencies>
  <dependency>
    <groupId>com.rapid7</groupId>
    <artifactId>codesign-maven-plugin</artifactId>
    <version>0.1.0</version>
  </dependency>
</dependencies>
```


*NOTE:* This plugin was adapted from https://github.com/cjnygard/rest-maven-plugin.
