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

package com.softtek_jare.mcp;

import com.softtek_jare.mcp.model.ProjectLoadException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code LombokDelomboker} class.
 *
 * @author Alejandro Ferreira
 */
public class LombokDelomboker {

    private static final Logger LOG = LoggerFactory.getLogger(LombokDelomboker.class);

    private static final String MAVEN_CENTRAL_LOMBOK_URL =
            "https://repo1.maven.org/maven2/org/projectlombok/lombok/%s/lombok-%s.jar";
    private static final Path LOMBOK_CACHE_DIR =
            Path.of(System.getProperty("user.home"), ".java-mcp-server", "lombok");
    private static final Path DELOMBOK_CACHE_DIR =
            Path.of(System.getProperty("user.home"), ".java-mcp-server", "delombok");
    private static final Path CLASSPATH_CACHE_DIR =
            Path.of(System.getProperty("user.home"), ".java-mcp-server", "classpath");
    private static final long DEFAULT_TIMEOUT_SECONDS = 300;
    private static final String DEFAULT_LOMBOK_VERSION = "1.18.46";
    private static final String MIN_LOMBOK_VERSION_JDK17 = "1.18.30";

    private final LombokDetector detector = new LombokDetector();

/**
 * Checks whether a Lombok JAR is available in the cache or on the system classpath.
 */
    public boolean isLombokAvailable() {
        return findCachedOrSystemLombok() != null;
    }

/**
 * Runs delombok on a project directory, defaulting to RAW build type.
 */
    public Path delombok(Path projectDir) throws ProjectLoadException {
        return delombok(projectDir, BuildDetector.BuildType.RAW);
    }

/**
 * Runs delombok on a project, resolving the Lombok JAR and classpath as needed.
 */
    public Path delombok(Path projectDir, BuildDetector.BuildType buildType) throws ProjectLoadException {
        if (projectDir == null || !Files.isDirectory(projectDir)) {
            throw new ProjectLoadException("LOMBOK_INPUT_INVALID",
                    "project directory not found: " + projectDir);
        }

        LombokDetector.LombokInfo info = detector.detect(projectDir);
        if (!info.present() && !detector.hasLombokInSources(projectDir)) {
            throw new ProjectLoadException("LOMBOK_NOT_DETECTED",
                    "no Lombok usage detected in " + projectDir);
        }

        String version = LombokDetector.normalizeVersion(info.version());
        if (version == null) {
            version = DEFAULT_LOMBOK_VERSION;
        }

        String effectiveVersion = effectiveLombokVersion(version);
        if (!effectiveVersion.equals(version)) {
            LOG.warn("Project's Lombok version {} is too old for JDK 17+; using {} instead",
                    version, effectiveVersion);
        }

        Path lombokJar = resolveLombokJar(effectiveVersion);
        if (lombokJar == null || !Files.isRegularFile(lombokJar)) {
            throw new ProjectLoadException("LOMBOK_JAR_MISSING",
                    "could not locate or download lombok JAR (version=" + effectiveVersion + "). "
                            + "Provide lombok manually in " + LOMBOK_CACHE_DIR + " or set network access.");
        }

        Path srcRoot = findSourceRoot(projectDir);
        if (srcRoot == null) {
            throw new ProjectLoadException("LOMBOK_NO_SOURCES",
                    "no Java source directory found under " + projectDir);
        }

        Path dest = DELOMBOK_CACHE_DIR.resolve(
                projectDir.getFileName().toString() + "_" + version + "_"
                        + System.currentTimeMillis());
        try {
            Files.createDirectories(dest);
        } catch (IOException e) {
            throw new ProjectLoadException("LOMBOK_CACHE_ERROR",
                    "could not create delombok output dir: " + e.getMessage());
        }

        String classpath = buildProjectClasspath(projectDir, buildType, lombokJar);
        runDelombok(lombokJar, srcRoot, dest, classpath);

        if (!hasJavaFiles(dest)) {
            try {
                deleteRecursively(dest);
            } catch (IOException ignored) {
            }
            throw new ProjectLoadException("LOMBOK_DELOMBOK_FAILED",
                    "delombok produced no .java files in " + dest
                            + ". Check lombok JAR version matches project.");
        }

        LOG.info("Delomboked project at {} into {} (classpath entries: {})",
                projectDir, dest, classpath.isEmpty() ? 0 : classpath.split(java.io.File.pathSeparator).length);
        return dest;
    }

    /**
     * Builds a classpath string for the project so delombok can resolve external types
     * referenced by Lombok-generated code (e.g. @JBossLog → org.jboss.logging.Logger).
     *
     * Strategy by build type:
     * - MAVEN: offline-first via `mvn -q -o dependency:build-classpath`. Falls back to
     *          online if offline fails. Output is written to a temp file to avoid
     *          command-line length limits.
     * - GRADLE: uses an init script that adds a `printClasspath` task printing
     *           `runtimeClasspath` (or `compileClasspath` as fallback). Output to stdout.
     * - ECLIPSE/ANT/RAW: no classpath (Lombok-generated code referencing external
     *   types will fail; existing fallback to original source applies).
     *
     * Always appends the project's own {@code target/classes} (or {@code build/classes/java/main}
     * for Gradle) if present, so delombok can resolve internal types.
     */
    public String buildProjectClasspath(Path projectDir, BuildDetector.BuildType buildType, Path lombokJar) {
        try {
            Files.createDirectories(CLASSPATH_CACHE_DIR);
        } catch (IOException e) {
            LOG.warn("Could not create classpath cache dir {}: {}", CLASSPATH_CACHE_DIR, e.getMessage());
        }

        List<String> entries = new ArrayList<>();
        entries.add(lombokJar.toAbsolutePath().toString());

        String resolved = null;
        if (buildType == BuildDetector.BuildType.MAVEN) {
            resolved = buildMavenClasspath(projectDir);
        } else if (buildType == BuildDetector.BuildType.GRADLE) {
            resolved = buildGradleClasspath(projectDir);
        }

        if (resolved != null && !resolved.isBlank()) {
            for (String e : resolved.split(java.io.File.pathSeparator)) {
                if (!e.isBlank() && !entries.contains(e)) entries.add(e);
            }
        }

        for (Path classesDir : List.of(
                projectDir.resolve("target/classes"),
                projectDir.resolve("build/classes/java/main"))) {
            if (Files.isDirectory(classesDir)) {
                String s = classesDir.toAbsolutePath().toString();
                if (!entries.contains(s)) entries.add(s);
            }
        }

        return String.join(java.io.File.pathSeparator, entries);
    }

/**
 * Builds a classpath string for a Maven project, using a cached result if available.
 */
    private String buildMavenClasspath(Path projectDir) {
        Path cpFile = CLASSPATH_CACHE_DIR.resolve(
                projectDir.getFileName().toString() + ".mvn.cp.txt");
        if (Files.isRegularFile(cpFile)) {
            try {
                String cached = Files.readString(cpFile, StandardCharsets.UTF_8).trim();
                if (!cached.isEmpty()) {
                    LOG.debug("Using cached Maven classpath for {} ({} chars)", projectDir, cached.length());
                    return cached;
                }
            } catch (IOException e) {
                LOG.debug("Could not read cached classpath: {}", e.getMessage());
            }
        }

        String output = runMavenBuildClasspath(projectDir, true);
        if (output == null) {
            LOG.info("Offline Maven classpath resolution failed for {}; falling back to online", projectDir);
            output = runMavenBuildClasspath(projectDir, false);
        }
        if (output != null && !output.isBlank()) {
            try {
                Files.writeString(cpFile, output, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOG.debug("Could not cache classpath: {}", e.getMessage());
            }
            return output;
        }
        return "";
    }

/**
 * Runs the Maven {@code dependency:build-classpath} goal and reads the output file.
 */
    private String runMavenBuildClasspath(Path projectDir, boolean offline) {
        Path tmp = CLASSPATH_CACHE_DIR.resolve(
                "mvn-cp-" + System.currentTimeMillis() + ".txt");
        List<String> cmd = new ArrayList<>();
        cmd.add("mvn");
        cmd.add("-q");
        if (offline) cmd.add("-o");
        cmd.add("-Dmdep.outputFile=" + tmp.toAbsolutePath());
        cmd.add("-DincludeScope=compile");
        cmd.add("dependency:build-classpath");

        LOG.info("Building Maven classpath (offline={}) for {}", offline, projectDir);
        return runAndReadFile(cmd, projectDir, tmp);
    }

/**
 * Builds a classpath string for a Gradle project via an init-script task.
 */
    private String buildGradleClasspath(Path projectDir) {
        Path cpFile = CLASSPATH_CACHE_DIR.resolve(
                projectDir.getFileName().toString() + ".gradle.cp.txt");
        if (Files.isRegularFile(cpFile)) {
            try {
                String cached = Files.readString(cpFile, StandardCharsets.UTF_8).trim();
                if (!cached.isEmpty()) {
                    LOG.debug("Using cached Gradle classpath for {} ({} chars)", projectDir, cached.length());
                    return cached;
                }
            } catch (IOException e) {
                LOG.debug("Could not read cached classpath: {}", e.getMessage());
            }
        }

        Path initScript;
        try {
            initScript = Files.createTempFile("java-mcp-gradle-init-", ".gradle");
            String initContent = "allprojects { afterEvaluate { proj ->\n"
                    + "  proj.task('printClasspath') {\n"
                    + "    doLast {\n"
                    + "      def cfg = proj.configurations.findByName('runtimeClasspath')\n"
                    + "        ?: proj.configurations.findByName('compileClasspath')\n"
                    + "      if (cfg != null) { println cfg.asPath }\n"
                    + "    }\n"
                    + "  }\n"
                    + "} }\n";
            Files.writeString(initScript, initContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Could not create Gradle init script: {}", e.getMessage());
            return "";
        }

        try {
            String gradleCmd = resolveGradleCommand(projectDir);
            if (gradleCmd == null) {
                LOG.info("No gradle wrapper or system gradle found for {}", projectDir);
                return "";
            }
            List<String> cmd = List.of(
                    gradleCmd, "-q", "--no-daemon",
                    "--offline",
                    "-I", initScript.toAbsolutePath().toString(),
                    "printClasspath");
            LOG.info("Building Gradle classpath (offline) for {} via {}", projectDir, gradleCmd);
            String output = runAndReadStdout(cmd, projectDir);
            if (output == null || output.isBlank()) {
                LOG.info("Offline Gradle classpath failed for {}; falling back to online", projectDir);
                List<String> onlineCmd = List.of(
                        gradleCmd, "-q", "--no-daemon",
                        "-I", initScript.toAbsolutePath().toString(),
                        "printClasspath");
                output = runAndReadStdout(onlineCmd, projectDir);
            }
            if (output != null && !output.isBlank()) {
                try {
                    Files.writeString(cpFile, output, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    LOG.debug("Could not cache classpath: {}", e.getMessage());
                }
                return output;
            }
        } finally {
            try { Files.deleteIfExists(initScript); } catch (IOException ignored) {}
        }
        return "";
    }

/**
 * Resolves the Gradle command (wrapper or system) for a project directory.
 */
    private String resolveGradleCommand(Path projectDir) {
        Path wrapper = projectDir.resolve("gradlew");
        if (Files.isRegularFile(wrapper) && Files.isExecutable(wrapper)) {
            return wrapper.toAbsolutePath().toString();
        }
        Path wrapperBat = projectDir.resolve("gradlew.bat");
        if (Files.isRegularFile(wrapperBat)) {
            return wrapperBat.toAbsolutePath().toString();
        }
        for (String name : new String[]{"gradle", "gradle.exe"}) {
            try {
                Process probe = new ProcessBuilder(name, "--version")
                        .redirectErrorStream(true).start();
                if (probe.waitFor(5, TimeUnit.SECONDS) && probe.exitValue() == 0) {
                    return name;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

/**
 * Runs a command, waits for completion, and reads the output file.
 */
    private String runAndReadFile(List<String> cmd, Path workDir, Path outputFile) {
        try {
            Process p = new ProcessBuilder(cmd).directory(workDir.toFile())
                    .redirectErrorStream(true).start();
            StringBuilder err = new StringBuilder();
            try (var reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    err.append(line).append('\n');
                }
            }
            if (!p.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                LOG.warn("Maven classpath build timed out after {}s", DEFAULT_TIMEOUT_SECONDS);
                return null;
            }
            if (p.exitValue() != 0) {
                String tail = err.length() > 1000 ? err.substring(err.length() - 1000) : err.toString();
                LOG.warn("Maven classpath build failed (exit={}). Tail: {}", p.exitValue(), tail);
                return null;
            }
            if (!Files.isRegularFile(outputFile)) {
                LOG.warn("Maven classpath build produced no output file at {}", outputFile);
                return null;
            }
            return Files.readString(outputFile, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            LOG.warn("Could not run Maven classpath build: {}", e.getMessage());
            return null;
        } finally {
            try { Files.deleteIfExists(outputFile); } catch (IOException ignored) {}
        }
    }

/**
 * Runs a command and reads its stdout.
 */
    private String runAndReadStdout(List<String> cmd, Path workDir) {
        try {
            Process p = new ProcessBuilder(cmd).directory(workDir.toFile())
                    .redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (var reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            if (!p.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                LOG.warn("Gradle classpath build timed out after {}s", DEFAULT_TIMEOUT_SECONDS);
                return null;
            }
            if (p.exitValue() != 0) return null;
            String result = out.toString().trim();
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            LOG.warn("Could not run Gradle classpath build: {}", e.getMessage());
            return null;
        }
    }

/**
 * Resolves a Lombok JAR for the given version, downloading it from Maven Central if needed.
 */
    public Path resolveLombokJar(String version) {
        String v = LombokDetector.normalizeVersion(version);
        if (v == null) v = DEFAULT_LOMBOK_VERSION;

        Path cached = LOMBOK_CACHE_DIR.resolve("lombok-" + v + ".jar");
        if (Files.isRegularFile(cached)) {
            return cached;
        }

        if (!v.equals(DEFAULT_LOMBOK_VERSION)) {
            Path defaultCached = LOMBOK_CACHE_DIR.resolve("lombok-" + DEFAULT_LOMBOK_VERSION + ".jar");
            if (Files.isRegularFile(defaultCached)) {
                LOG.debug("Requested lombok {} not in cache; falling back to cached {}",
                        v, DEFAULT_LOMBOK_VERSION);
                return defaultCached;
            }
        }

        Path systemWide = findSystemLombokJar();
        if (systemWide != null) {
            return systemWide;
        }

        try {
            Files.createDirectories(LOMBOK_CACHE_DIR);
        } catch (IOException e) {
            LOG.warn("Could not create lombok cache dir {}: {}", LOMBOK_CACHE_DIR, e.getMessage());
            return null;
        }

        String url = String.format(MAVEN_CENTRAL_LOMBOK_URL, v, v);
        LOG.info("Downloading lombok {} from {}", v, url);
        try (InputStream in = new URI(url).toURL().openStream()) {
            Files.copy(in, cached, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Downloaded lombok {} -> {}", v, cached);
            return cached;
        } catch (Exception e) {
            LOG.warn("Failed to download lombok {}: {}", v, e.getMessage());
            try {
                Files.deleteIfExists(cached);
            } catch (IOException ignored) {
            }
            return null;
        }
    }

/**
 * Finds a Lombok JAR in the cache or on the system classpath.
 */
    private Path findCachedOrSystemLombok() {
        if (Files.isDirectory(LOMBOK_CACHE_DIR)) {
            try (var stream = Files.list(LOMBOK_CACHE_DIR)) {
                var match = stream
                        .filter(p -> p.getFileName().toString().startsWith("lombok-")
                                && p.getFileName().toString().endsWith(".jar"))
                        .max(Comparator.comparing(p -> p.getFileName().toString()));
                if (match.isPresent()) return match.get();
            } catch (IOException e) {
                LOG.debug("Could not list lombok cache: {}", e.getMessage());
            }
        }
        return findSystemLombokJar();
    }

/**
 * Searches the Java classpath for a Lombok JAR.
 */
    private Path findSystemLombokJar() {
        String classpath = System.getProperty("java.class.path", "");
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            if (entry == null || entry.isBlank()) continue;
            String name = Path.of(entry).getFileName().toString();
            if (name.startsWith("lombok-") && name.endsWith(".jar")) {
                Path p = Path.of(entry);
                if (Files.isRegularFile(p)) return p;
            }
        }
        return null;
    }

/**
 * Finds the source root directory within a project.
 */
    private Path findSourceRoot(Path projectDir) {
        String[] candidates = {
                "src/main/java",
                "src",
                "src/main/kotlin",
                "src/main/groovy"
        };
        for (String rel : candidates) {
            Path p = projectDir.resolve(rel);
            if (Files.isDirectory(p) && hasJavaFiles(p)) {
                return p;
            }
        }
        if (hasJavaFiles(projectDir)) {
            return projectDir;
        }
        return null;
    }

/**
 * Checks whether a directory tree contains any {@code .java} files.
 */
    private boolean hasJavaFiles(Path dir) {
        try (var stream = Files.walk(dir, 5)) {
            return stream.anyMatch(p -> p.toString().endsWith(".java"));
        } catch (IOException e) {
            return false;
        }
    }

/**
 * Launches the delombok process with the given JAR, source root, and classpath.
 */
    private void runDelombok(Path lombokJar, Path srcRoot, Path destDir, String classpath) throws ProjectLoadException {
        List<String> cmd = new ArrayList<>();
        cmd.add("java");
        addAddOpensFlags(cmd);
        if (classpath != null && !classpath.isEmpty()) {
            cmd.add("-cp");
            cmd.add(classpath);
            cmd.add("lombok.launch.Main");
            LOG.info("Running delombok with classpath ({} entries)", classpath.split(java.io.File.pathSeparator).length);
        } else {
            cmd.add("-jar");
            cmd.add(lombokJar.toAbsolutePath().toString());
            cmd.add("-n");
            LOG.warn("Running delombok WITHOUT classpath — external types in Lombok-generated code will fail");
        }
        cmd.add("delombok");
        cmd.add(srcRoot.toAbsolutePath().toString());
        cmd.add("-d");
        cmd.add(destDir.toAbsolutePath().toString());

        LOG.debug("Delombok cmd: {}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new ProjectLoadException("LOMBOK_DELOMBOK_LAUNCH",
                    "could not launch delombok process: " + e.getMessage());
        }

        String output;
        try (InputStream is = process.getInputStream()) {
            output = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            LOG.warn("Could not read delombok output: {}", e.getMessage());
            output = "";
        }

        boolean finished;
        try {
            finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new ProjectLoadException("LOMBOK_DELOMBOK_INTERRUPTED",
                    "delombok was interrupted");
        }

        if (!finished) {
            process.destroyForcibly();
            throw new ProjectLoadException("LOMBOK_DELOMBOK_TIMEOUT",
                    "delombok exceeded " + DEFAULT_TIMEOUT_SECONDS + "s timeout");
        }

        int exit = process.exitValue();
        if (exit != 0) {
            String tail = output.length() > 2000
                    ? output.substring(output.length() - 2000)
                    : output;
            throw new ProjectLoadException("LOMBOK_DELOMBOK_FAILED",
                    "delombok exited with code " + exit + ". Output tail:\n" + tail);
        }
    }

    /**
     * Returns the Lombok version to use. If the requested version is older than the
     * minimum required for the current JDK (17+), returns the default instead.
     */
    static String effectiveLombokVersion(String requested) {
        String v = LombokDetector.normalizeVersion(requested);
        if (v == null) return DEFAULT_LOMBOK_VERSION;
        int[] req = parseVersion(v);
        int[] min = parseVersion(MIN_LOMBOK_VERSION_JDK17);
        if (compareVersions(req, min) < 0) {
            return DEFAULT_LOMBOK_VERSION;
        }
        return v;
    }

    private static int[] parseVersion(String v) {
        String[] parts = v.split("\\.");
        int[] out = new int[Math.max(3, parts.length)];
        for (int i = 0; i < parts.length; i++) {
            StringBuilder num = new StringBuilder();
            for (char c : parts[i].toCharArray()) {
                if (Character.isDigit(c)) num.append(c);
                else break;
            }
            try { out[i] = Integer.parseInt(num.toString()); } catch (NumberFormatException e) { out[i] = 0; }
        }
        return out;
    }

/**
 * Compares two version arrays element by element.
 */
    private static int compareVersions(int[] a, int[] b) {
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    /**
     * Adds the {@code --add-opens} JVM flags required for Lombok (especially 1.18.2)
     * to access internal {@code jdk.compiler} packages when running on JDK 9+.
     * Without these flags, delombok throws {@code IllegalAccessError} on JDK 17+.
     */
    private static void addAddOpensFlags(List<String> cmd) {
        for (String pkg : new String[]{
                "com.sun.tools.javac.tree",
                "com.sun.tools.javac.processing",
                "com.sun.tools.javac.code",
                "com.sun.tools.javac.comp",
                "com.sun.tools.javac.file",
                "com.sun.tools.javac.main",
                "com.sun.tools.javac.util",
                "com.sun.tools.javac.parser",
                "com.sun.tools.javac.jvm",
                "com.sun.tools.javac.api"
        }) {
            cmd.add("--add-opens");
            cmd.add("jdk.compiler/" + pkg + "=ALL-UNNAMED");
        }
    }

    public void cleanup(Path delombokedDir) {
        if (delombokedDir == null) return;
        if (!delombokedDir.startsWith(DELOMBOK_CACHE_DIR)) return;
        try {
            deleteRecursively(delombokedDir);
            LOG.info("Cleaned up delombok output: {}", delombokedDir);
        } catch (IOException e) {
            LOG.warn("Could not clean up {}: {}", delombokedDir, e.getMessage());
        }
    }

/**
 * Recursively deletes a directory tree.
 */
    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }
}
