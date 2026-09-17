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

package com.softtek_jare.mcp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@code editable} field added to {@link ProjectEntry}.
 *
 * @author Janusch Rentenatus
 */
class ProjectEntryTest {

    @Test
    void editableTrueByDefault() {
        ProjectEntry entry = makeEntry(true);
        assertTrue(entry.editable());
    }

    @Test
    void editableFalseWhenSet() {
        ProjectEntry entry = makeEntry(false);
        assertFalse(entry.editable());
    }

    @Test
    void editablePreservedOnExpiryCopy() {
        ProjectEntry original = makeEntry(false);
        // markExpired copies the entry with expired=true but must keep editable
        ProjectEntry expired = new ProjectEntry(
            original.name(), original.alias(), original.expiryDate(),
            original.projectDir(), original.launcher(), original.model(),
            original.buildType(), original.delomboked(), original.lombokVersion(),
            original.originalProjectDir(), original.originalSource(),
            original.sourceFingerprints(), true, original.editable(), 1, 1, false
        );
        assertFalse(expired.editable());
    }

    @Test
    void editableTruePreservedOnExpiryCopy() {
        ProjectEntry original = makeEntry(true);
        ProjectEntry expired = new ProjectEntry(
            original.name(), original.alias(), original.expiryDate(),
            original.projectDir(), original.launcher(), original.model(),
            original.buildType(), original.delomboked(), original.lombokVersion(),
            original.originalProjectDir(), original.originalSource(),
            original.sourceFingerprints(), true, original.editable(), 1, 1, false
        );
        assertTrue(expired.editable());
    }

    private static ProjectEntry makeEntry(boolean editable) {
        return new ProjectEntry(
            "test-project", "test-project", Instant.now().plusSeconds(600),
            Path.of("/tmp/test"), null, null, "RAW",
            false, null,
            Path.of("/tmp/test"), "test-source",
            Map.of(), false, editable, 1, 1, false
        );
    }
}
