package com.softtek.mcp;

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
}
