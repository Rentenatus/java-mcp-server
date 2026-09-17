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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * The {@code LombokDetectorTest} class.
 *
 * @author Alejandro Ferreira
 */
class LombokDetectorTest {

    private final LombokDetector detector = new LombokDetector();

/**
 * Verifies that Lombok is detected in a Maven project via pom.xml.
 */
    @Test
    void detectsLombokInMavenProject() {
        Path project = Paths.get("src/test/resources/lombok-sample").toAbsolutePath();
        LombokDetector.LombokInfo info = detector.detect(project);
        assertTrue(info.present(), "Lombok should be detected in pom.xml");
        assertEquals("1.18.46", info.version(), "Lombok version should match pom.xml");
        assertEquals("pom.xml", info.source());
    }

/**
 * Verifies that Lombok is not detected in a plain project without Lombok dependencies.
 */
    @Test
    void doesNotDetectLombokInPlainProject() {
        Path project = Paths.get("src/test/resources/no-lombok-sample").toAbsolutePath();
        LombokDetector.LombokInfo info = detector.detect(project);
        assertFalse(info.present(), "Lombok should NOT be detected");
    }

/**
 * Verifies that Lombok annotations in source files are detected.
 */
    @Test
    void detectsLombokUsageInSources() {
        Path project = Paths.get("src/test/resources/lombok-sample").toAbsolutePath();
        assertTrue(detector.hasLombokInSources(project),
                "Source code uses @Data and @Builder; should be detected");
    }

/**
 * Verifies that source files without Lombok are not flagged.
 */
    @Test
    void doesNotDetectLombokUsageInPlainSources() {
        Path project = Paths.get("src/test/resources/no-lombok-sample").toAbsolutePath();
        assertFalse(detector.hasLombokInSources(project),
                "Source code has no Lombok annotations");
    }

/**
 * Verifies that null and non-existent paths are handled gracefully.
 */
    @Test
    void handlesNullAndInvalidInputs() {
        assertFalse(detector.detect(null).present());
        assertFalse(detector.detect(Paths.get("/nonexistent/path")).present());
        assertFalse(detector.hasLombokInSources(null));
    }

/**
 * Verifies that version normalization strips prefixes and whitespace.
 */
    @Test
    void normalizeVersionStripsPrefixAndWhitespace() {
        assertEquals("1.18.46", LombokDetector.normalizeVersion("v1.18.46"));
        assertEquals("1.18.46", LombokDetector.normalizeVersion("  1.18.46  "));
        assertEquals("1.18.46", LombokDetector.normalizeVersion("1.18.46"));
        assertEquals(null, LombokDetector.normalizeVersion(""));
        assertEquals(null, LombokDetector.normalizeVersion("   "));
    }
}
