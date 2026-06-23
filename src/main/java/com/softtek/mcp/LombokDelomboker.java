package com.softtek.mcp;

import com.softtek.mcp.model.ProjectLoadException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LombokDelomboker {

    private static final Logger LOG = LoggerFactory.getLogger(LombokDelomboker.class);

    private static final String MAVEN_CENTRAL_LOMBOK_URL =
            "https://repo1.maven.org/maven2/org/projectlombok/lombok/%s/lombok-%s.jar";
    private static final String LOMBOK_GROUP = "org.projectlombok";
    private static final String LOMBOK_ARTIFACT = "lombok";
    private static final Path LOMBOK_CACHE_DIR =
            Path.of(System.getProperty("user.home"), ".java-mcp-server", "lombok");
    private static final Path DELOMBOK_CACHE_DIR =
            Path.of(System.getProperty("user.home"), ".java-mcp-server", "delombok");
    private static final long DEFAULT_TIMEOUT_SECONDS = 120;
    private static final String DEFAULT_LOMBOK_VERSION = "1.18.46";

    private final LombokDetector detector = new LombokDetector();

    public boolean isLombokAvailable() {
        return findCachedOrSystemLombok() != null;
    }

    public Path delombok(Path projectDir) throws ProjectLoadException {
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

        Path lombokJar = resolveLombokJar(version);
        if (lombokJar == null || !Files.isRegularFile(lombokJar)) {
            throw new ProjectLoadException("LOMBOK_JAR_MISSING",
                    "could not locate or download lombok JAR (version=" + version + "). "
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

        runDelombok(lombokJar, srcRoot, dest);

        if (!hasJavaFiles(dest)) {
            try {
                deleteRecursively(dest);
            } catch (IOException ignored) {
            }
            throw new ProjectLoadException("LOMBOK_DELOMBOK_FAILED",
                    "delombok produced no .java files in " + dest
                            + ". Check lombok JAR version matches project.");
        }

        LOG.info("Delomboked project at {} into {}", projectDir, dest);
        return dest;
    }

    public Path resolveLombokJar(String version) {
        String v = LombokDetector.normalizeVersion(version);
        if (v == null) v = DEFAULT_LOMBOK_VERSION;

        Path cached = LOMBOK_CACHE_DIR.resolve("lombok-" + v + ".jar");
        if (Files.isRegularFile(cached)) {
            return cached;
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
        try (InputStream in = new java.net.URI(url).toURL().openStream()) {
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

    private boolean hasJavaFiles(Path dir) {
        try (var stream = Files.walk(dir, 5)) {
            return stream.anyMatch(p -> p.toString().endsWith(".java"));
        } catch (IOException e) {
            return false;
        }
    }

    private void runDelombok(Path lombokJar, Path srcRoot, Path destDir) throws ProjectLoadException {
        java.util.List<String> cmd = java.util.List.of(
                "java",
                "-jar", lombokJar.toAbsolutePath().toString(),
                "delombok",
                srcRoot.toAbsolutePath().toString(),
                "-d", destDir.toAbsolutePath().toString(),
                "-n"
        );
        LOG.info("Running delombok: {}", String.join(" ", cmd));

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
