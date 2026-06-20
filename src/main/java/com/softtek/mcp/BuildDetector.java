package com.softtek.mcp;

import java.nio.file.Files;
import java.nio.file.Path;

public class BuildDetector {

    public enum BuildType {
        MAVEN,
        GRADLE,
        ECLIPSE,
        ANT,
        RAW
    }

    public record BuildInfo(BuildType type, String detail, Path projectDir) {}

    public BuildInfo detect(Path dir) {
        if (hasFile(dir, "pom.xml")) {
            return new BuildInfo(BuildType.MAVEN, "Maven", dir);
        }
        if (hasFile(dir, "build.gradle") || hasFile(dir, "build.gradle.kts")) {
            return new BuildInfo(BuildType.GRADLE, "Gradle", dir);
        }
        if (hasFile(dir, ".project") && hasFile(dir, ".classpath")) {
            return new BuildInfo(BuildType.ECLIPSE, "Eclipse", dir);
        }
        if (hasFile(dir, "build.xml") && hasFile(dir, "ivy.xml")) {
            return new BuildInfo(BuildType.ANT, "Ant + Ivy", dir);
        }
        if (hasFile(dir, "build.xml")) {
            return new BuildInfo(BuildType.ANT, "Ant", dir);
        }
        return new BuildInfo(BuildType.RAW, "Raw (no build system detected)", dir);
    }

    public Path findSourceDir(BuildInfo info) {
        if (info.type() == BuildType.MAVEN) {
            Path src = info.projectDir().resolve("src/main/java");
            if (Files.isDirectory(src)) return src;
        }
        return info.projectDir();
    }

    private boolean hasFile(Path dir, String name) {
        return Files.exists(dir.resolve(name));
    }
}
