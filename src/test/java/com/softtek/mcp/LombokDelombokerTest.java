package com.softtek.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.softtek.mcp.model.ProjectLoadException;

import org.junit.jupiter.api.Test;

class LombokDelombokerTest {

    private final LombokDelomboker delomboker = new LombokDelomboker();

    @Test
    void resolvesLombokJarFromCacheOrDownloads() {
        Path jar = delomboker.resolveLombokJar("1.18.46");
        assumeTrue(jar != null && Files.isRegularFile(jar),
                "lombok JAR must be available locally (cache or download); skipping");
        assertNotNull(jar);
        assertTrue(jar.getFileName().toString().startsWith("lombok-"),
                "Filename should start with 'lombok-', got: " + jar.getFileName());
        assertTrue(jar.getFileName().toString().endsWith(".jar"));
    }

    @Test
    void delombokProducesExpandedSources() throws Exception {
        Path project = Paths.get("src/test/resources/lombok-sample").toAbsolutePath();
        Path jar = delomboker.resolveLombokJar("1.18.46");
        assumeTrue(jar != null && Files.isRegularFile(jar),
                "lombok JAR unavailable; skipping integration test");

        Path out = delomboker.delombok(project);
        assertNotNull(out);
        assertTrue(Files.isDirectory(out), "delombok output should be a directory");
        assertTrue(out.toString().contains("lombok-sample"),
                "delombok output should be under a lombok-sample-named directory");
        try (var walk = Files.walk(out)) {
            long javaCount = walk.filter(p -> p.toString().endsWith(".java")).count();
            assertTrue(javaCount >= 1, "Expected at least one .java file in delomboked output");
        } finally {
            delomboker.cleanup(out);
        }
    }

    @Test
    void delombokExpandsJBossLogWithClasspath() throws Exception {
        Path project = Paths.get("src/test/resources/lombok-bosslog-sample").toAbsolutePath();
        Path jar = delomboker.resolveLombokJar("1.18.46");
        assumeTrue(jar != null && Files.isRegularFile(jar),
                "lombok JAR unavailable; skipping integration test");
        assumeTrue(isMavenAvailable(),
                "Maven unavailable; skipping classpath-aware delombok test");

        Path out = delomboker.delombok(project, BuildDetector.BuildType.MAVEN);
        assertNotNull(out);
        assertTrue(Files.isDirectory(out));
        try (var walk = Files.walk(out)) {
            long javaCount = walk.filter(p -> p.toString().endsWith(".java")).count();
            assertTrue(javaCount >= 1, "Expected at least one .java file in delomboked output");

            Path bossLogUser = out.resolve("com/example/BossLogUser.java");
            assertTrue(Files.isRegularFile(bossLogUser),
                    "BossLogUser.java should be in delomboked output");
            String content = Files.readString(bossLogUser);
            assertTrue(content.contains("org.jboss.logging.Logger"),
                    "Delomboked source should reference jboss-logging Logger, got:\n"
                            + content.substring(0, Math.min(500, content.length())));
            assertTrue(!content.contains("@JBossLog"),
                    "Delomboked source should no longer contain @JBossLog annotation");
        } finally {
            delomboker.cleanup(out);
        }
    }

    @Test
    void buildMavenClasspathReturnsEntriesForMavenProject() throws Exception {
        Path project = Paths.get("src/test/resources/lombok-bosslog-sample").toAbsolutePath();
        assumeTrue(isMavenAvailable(), "Maven unavailable; skipping classpath build test");

        Path lombokJar = delomboker.resolveLombokJar("1.18.46");
        assumeTrue(lombokJar != null && Files.isRegularFile(lombokJar),
                "lombok JAR unavailable; skipping");
        String cp = delomboker.buildProjectClasspath(project, BuildDetector.BuildType.MAVEN, lombokJar);
        assertNotNull(cp);
        assertTrue(cp.contains("lombok-1.18.46.jar"),
                "Classpath should include the lombok JAR, got: " + cp.substring(0, Math.min(200, cp.length())));
        assertTrue(cp.contains("jboss-logging"),
                "Classpath should resolve jboss-logging dependency, got: " + cp);
    }

    @Test
    void buildGradleClasspathReturnsEmptyForMavenProject() throws Exception {
        Path project = Paths.get("src/test/resources/lombok-bosslog-sample").toAbsolutePath();
        Path lombokJar = delomboker.resolveLombokJar("1.18.46");
        assumeTrue(lombokJar != null && Files.isRegularFile(lombokJar),
                "lombok JAR unavailable; skipping");

        String cp = delomboker.buildProjectClasspath(project, BuildDetector.BuildType.GRADLE, lombokJar);
        assertNotNull(cp);
        assertTrue(cp.contains("lombok-1.18.46.jar"));
        assertEquals(1, cp.split(java.io.File.pathSeparator).length,
                "For Gradle build type applied to a Maven project, only lombok jar should be in classpath");
    }

    @Test
    void rawBuildTypeYieldsMinimalClasspath() throws Exception {
        Path project = Paths.get("src/test/resources/lombok-bosslog-sample").toAbsolutePath();
        Path lombokJar = delomboker.resolveLombokJar("1.18.46");
        assumeTrue(lombokJar != null && Files.isRegularFile(lombokJar),
                "lombok JAR unavailable; skipping");

        String cp = delomboker.buildProjectClasspath(project, BuildDetector.BuildType.RAW, lombokJar);
        assertNotNull(cp);
        assertEquals(1, cp.split(java.io.File.pathSeparator).length,
                "RAW build should not add anything beyond the lombok jar");
    }

    @Test
    void rawDelombokFallsBackToNoClasspath() throws Exception {
        Path project = Paths.get("src/test/resources/lombok-bosslog-sample").toAbsolutePath();
        Path jar = delomboker.resolveLombokJar("1.18.46");
        assumeTrue(jar != null && Files.isRegularFile(jar),
                "lombok JAR unavailable; skipping integration test");

        Path out = delomboker.delombok(project, BuildDetector.BuildType.RAW);
        assertNotNull(out);
        try {
            assertTrue(Files.isDirectory(out));
        } finally {
            delomboker.cleanup(out);
        }
    }

    private static boolean isMavenAvailable() {
        try {
            Process p = new ProcessBuilder("mvn", "-v").redirectErrorStream(true).start();
            return p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
