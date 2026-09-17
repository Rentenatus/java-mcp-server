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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.softtek_jare.mcp.model.Fingerprint;
import com.softtek_jare.mcp.model.ProjectEntry;

/**
 * Tests for {@link BaseJavaTool#validateFingerprint} — the 4-condition
 * optimistic-lock check from the edit-tools concept Section 5.
 *
 * @author Janusch Rentenatus
 */
class FingerprintValidationTest {

    @TempDir
    Path tempDir;

    @Test
    void condition1_expectedMatchesProceeds() throws Exception {
        Path file = tempDir.resolve("A.java");
        Files.writeString(file, "class A {}");
        Fingerprint fp = new Fingerprint(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        ProjectEntry entry = makeEntry(Map.of(file.normalize(), fp));
        String expected = BaseJavaTool.fingerprintToString(fp);

        assertDoesNotThrow(() -> BaseJavaTool.validateFingerprint(entry, file, expected));
    }

    @Test
    void condition2_notProvidedDiskMatchesProceeds() throws Exception {
        Path file = tempDir.resolve("B.java");
        Files.writeString(file, "class B {}");
        Fingerprint fp = new Fingerprint(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        ProjectEntry entry = makeEntry(Map.of(file.normalize(), fp));

        assertDoesNotThrow(() -> BaseJavaTool.validateFingerprint(entry, file, null));
        assertDoesNotThrow(() -> BaseJavaTool.validateFingerprint(entry, file, ""));
        assertDoesNotThrow(() -> BaseJavaTool.validateFingerprint(entry, file, "   "));
    }

    @Test
    void condition3_expectedDoesNotMatchAborts() throws Exception {
        Path file = tempDir.resolve("C.java");
        Files.writeString(file, "class C {}");
        Fingerprint fp = new Fingerprint(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        ProjectEntry entry = makeEntry(Map.of(file.normalize(), fp));

        String wrongFingerprint = "99999|99999";
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> BaseJavaTool.validateFingerprint(entry, file, wrongFingerprint)
        );
        assert ex.getMessage().contains("modified since the caller's last observation");
    }

    @Test
    void condition4_diskChangedAbortsRegardlessOfExpected() throws Exception {
        Path file = tempDir.resolve("D.java");
        Files.writeString(file, "class D {}");
        Fingerprint stale = new Fingerprint(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        ProjectEntry entry = makeEntry(Map.of(file.normalize(), stale));

        // Modify the file on disk after fingerprint was taken
        Thread.sleep(50);
        Files.writeString(file, "class D { int x; }");

        // Should abort even with matching "expected" fingerprint (because disk moved)
        String expected = BaseJavaTool.fingerprintToString(stale);
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> BaseJavaTool.validateFingerprint(entry, file, expected)
        );
        assert ex.getMessage().contains("fingerprint mismatch");
    }

    @Test
    void condition4_diskChangedAbortsEvenWithoutExpected() throws Exception {
        Path file = tempDir.resolve("E.java");
        Files.writeString(file, "class E {}");
        Fingerprint stale = new Fingerprint(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        ProjectEntry entry = makeEntry(Map.of(file.normalize(), stale));

        Thread.sleep(50);
        Files.writeString(file, "class E { int y; }");

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> BaseJavaTool.validateFingerprint(entry, file, null)
        );
        assert ex.getMessage().contains("fingerprint mismatch");
    }

    @Test
    void fileDeletedAborts() throws Exception {
        Path file = tempDir.resolve("F.java");
        Files.writeString(file, "class F {}");
        Fingerprint fp = new Fingerprint(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        ProjectEntry entry = makeEntry(Map.of(file.normalize(), fp));

        Files.delete(file);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> BaseJavaTool.validateFingerprint(entry, file, null)
        );
        assert ex.getMessage().contains("deleted");
    }

    @Test
    void noStoredFingerprintSkipsCheck() throws Exception {
        Path file = tempDir.resolve("G.java");
        Files.writeString(file, "class G {}");
        ProjectEntry entry = makeEntry(new HashMap<>());

        assertDoesNotThrow(() -> BaseJavaTool.validateFingerprint(entry, file, null));
    }

    @Test
    void fingerprintToStringFormat() {
        Fingerprint fp = new Fingerprint(123456789L, 4096L);
        assertEquals("123456789|4096", BaseJavaTool.fingerprintToString(fp));
    }

    private static ProjectEntry makeEntry(Map<Path, Fingerprint> fingerprints) {
        return new ProjectEntry(
            "test-project", "test-project", Instant.now().plusSeconds(600),
            Path.of("/tmp/test"), null, null, "RAW",
            false, null,
            Path.of("/tmp/test"), "test-source",
            fingerprints, false, true, 1, 1
        );
    }
}
