/*
 * MIT License
 *
 * Copyright (c) 2026 Alejandro Ferreira
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.softtek.mcp;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The {@code BuildDetector} class.
 *
 * @author Alejandro Ferreira
 */
public class BuildDetector {

    public enum BuildType {
        MAVEN,
        GRADLE,
        ECLIPSE,
        ANT,
        RAW
    }

    public record BuildInfo(BuildType type, String detail, Path projectDir) {}

/**
 * Detects the build system used in the given project directory.
 */
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

/**
 * Finds the main Java source directory for a detected build.
 */
    public Path findSourceDir(BuildInfo info) {
        if (info.type() == BuildType.MAVEN) {
            Path src = info.projectDir().resolve("src/main/java");
            if (Files.isDirectory(src)) return src;
        }
        return info.projectDir();
    }

/**
 * Checks whether a file with the given name exists in the directory.
 */
    private boolean hasFile(Path dir, String name) {
        return Files.exists(dir.resolve(name));
    }
}
