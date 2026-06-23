package com.softtek.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class LombokDetectorTest {

    private final LombokDetector detector = new LombokDetector();

    @Test
    void detectsLombokInMavenProject() {
        Path project = Paths.get("src/test/resources/lombok-sample").toAbsolutePath();
        LombokDetector.LombokInfo info = detector.detect(project);
        assertTrue(info.present(), "Lombok should be detected in pom.xml");
        assertEquals("1.18.46", info.version(), "Lombok version should match pom.xml");
        assertEquals("pom.xml", info.source());
    }

    @Test
    void doesNotDetectLombokInPlainProject() {
        Path project = Paths.get("src/test/resources/no-lombok-sample").toAbsolutePath();
        LombokDetector.LombokInfo info = detector.detect(project);
        assertFalse(info.present(), "Lombok should NOT be detected");
    }

    @Test
    void detectsLombokUsageInSources() {
        Path project = Paths.get("src/test/resources/lombok-sample").toAbsolutePath();
        assertTrue(detector.hasLombokInSources(project),
                "Source code uses @Data and @Builder; should be detected");
    }

    @Test
    void doesNotDetectLombokUsageInPlainSources() {
        Path project = Paths.get("src/test/resources/no-lombok-sample").toAbsolutePath();
        assertFalse(detector.hasLombokInSources(project),
                "Source code has no Lombok annotations");
    }

    @Test
    void handlesNullAndInvalidInputs() {
        assertFalse(detector.detect(null).present());
        assertFalse(detector.detect(Paths.get("/nonexistent/path")).present());
        assertFalse(detector.hasLombokInSources(null));
    }

    @Test
    void normalizeVersionStripsPrefixAndWhitespace() {
        assertEquals("1.18.46", LombokDetector.normalizeVersion("v1.18.46"));
        assertEquals("1.18.46", LombokDetector.normalizeVersion("  1.18.46  "));
        assertEquals("1.18.46", LombokDetector.normalizeVersion("1.18.46"));
        assertEquals(null, LombokDetector.normalizeVersion(""));
        assertEquals(null, LombokDetector.normalizeVersion("   "));
    }
}
