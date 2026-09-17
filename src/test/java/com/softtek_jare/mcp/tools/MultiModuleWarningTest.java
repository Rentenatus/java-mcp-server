/*
 * MIT License
 *
 * Copyright (c) 2026 Janusch Rentenatus
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

package com.softtek_jare.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.softtek_jare.mcp.model.Fingerprint;
import com.softtek_jare.mcp.model.ProjectEntry;

/**
 * Tests for {@link BaseJavaTool#formatMultiModuleWarning}.
 *
 * @author Janusch Rentenatus
 */
class MultiModuleWarningTest {

    @Test
    void noWarningWhenSingleModule() {
        ProjectEntry entry = makeEntry(1, 1);
        assertEquals("", BaseJavaTool.formatMultiModuleWarning(entry));
    }

    @Test
    void noWarningWhenModulesEqual() {
        ProjectEntry entry = makeEntry(3, 3);
        assertEquals("", BaseJavaTool.formatMultiModuleWarning(entry));
    }

    @Test
    void warningWhenMoreDetectedThanLoaded() {
        ProjectEntry entry = makeEntry(3, 1);
        String warning = BaseJavaTool.formatMultiModuleWarning(entry);
        assertTrue(warning.contains("3 modules detected"));
        assertTrue(warning.contains("1 loaded"));
        assertTrue(warning.contains("2 unloaded modules"));
        assertTrue(warning.contains("Load all modules"));
    }

    @Test
    void warningUsesSingularForOneUnloaded() {
        ProjectEntry entry = makeEntry(2, 1);
        String warning = BaseJavaTool.formatMultiModuleWarning(entry);
        assertTrue(warning.contains("1 unloaded module."));
        assertFalse(warning.contains("1 unloaded modules."));
    }

    private static ProjectEntry makeEntry(int modulesDetected, int modulesLoaded) {
        return new ProjectEntry(
            "test-project", "test-project", Instant.now().plusSeconds(600),
            Path.of("/tmp/test"), null, null, "MAVEN",
            false, null,
            Path.of("/tmp/test"), "test-source",
            Map.of(), false, true, modulesDetected, modulesLoaded
        );
    }
}
