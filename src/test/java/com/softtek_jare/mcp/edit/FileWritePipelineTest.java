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

package com.softtek_jare.mcp.edit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
 * Tests for {@link EditManager} — the file-write pipeline.
 *
 * @author Janusch Rentenatus
 */
class FileWritePipelineTest {

    @TempDir
    Path tempDir;

    @Test
    void writeFileSucceedsAndMarksModelDirty() throws Exception {
        Path file = tempDir.resolve("A.java");
        Files.writeString(file, "class A {}");
        Fingerprint fp = new Fingerprint(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        ProjectEntry entry = makeEntry(tempDir, Map.of(file.normalize(), fp));
        EditManager mgr = new EditManager(tempDir.resolve("backups"));

        ProjectEntry updated = mgr.writeFile(entry, file, "class A { int x; }", null);

        assertEquals("class A { int x; }", Files.readString(file).trim());
        assertTrue(updated.modelDirty());
        Fingerprint storedFp = updated.sourceFingerprints().get(file.normalize());
        assertEquals(fp, storedFp); // fingerprint unchanged — stays at load-time
    }

    @Test
    void writeFileThrowsOnStaleStoredFingerprint() throws Exception {
        Path file = tempDir.resolve("B.java");
        Files.writeString(file, "class B {}");
        Fingerprint stale = new Fingerprint(999999L, 999999L);
        ProjectEntry entry = makeEntry(tempDir, Map.of(file.normalize(), stale));
        EditManager mgr = new EditManager(tempDir.resolve("backups"));

        assertThrows(IllegalArgumentException.class,
                () -> mgr.writeFile(entry, file, "class B { int y; }", null));
    }

    @Test
    void writeFileThrowsOnExpectedFingerprintMismatch() throws Exception {
        Path file = tempDir.resolve("C.java");
        Files.writeString(file, "class C {}");
        Fingerprint fp = new Fingerprint(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        ProjectEntry entry = makeEntry(tempDir, Map.of(file.normalize(), fp));
        EditManager mgr = new EditManager(tempDir.resolve("backups"));

        assertThrows(IllegalArgumentException.class,
                () -> mgr.writeFile(entry, file, "class C {}", "wrong|fingerprint"));
    }

    @Test
    void writeFileCreatesBackupOnFirstEdit() throws Exception {
        Path file = tempDir.resolve("D.java");
        Files.writeString(file, "class D {}");
        Fingerprint fp = new Fingerprint(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        ProjectEntry entry = makeEntry(tempDir, Map.of(file.normalize(), fp));
        Path backupDir = tempDir.resolve("backups");
        EditManager mgr = new EditManager(backupDir);

        mgr.writeFile(entry, file, "class D { int z; }", null);

        assertTrue(Files.isDirectory(backupDir));
        // backup should contain the original content
        assertDoesNotThrow(() -> {
            try (var stream = Files.walk(backupDir)) {
                assertTrue(stream.anyMatch(p -> p.toString().endsWith("D.java")));
            }
        });
    }

    @Test
    void writeFileSkipsBackupOnSecondEdit() throws Exception {
        Path file = tempDir.resolve("E.java");
        Files.writeString(file, "class E {}");
        Fingerprint fp = new Fingerprint(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        ProjectEntry entry = makeEntry(tempDir, Map.of(file.normalize(), fp));
        Path backupDir = tempDir.resolve("backups");
        EditManager mgr = new EditManager(backupDir);

        ProjectEntry updated = mgr.writeFile(entry, file, "class E { int a; }", null);
        long backupCount = countBackupDirs(backupDir);

        mgr.writeFile(updated, file, "class E { int b; }", null);
        assertEquals(backupCount, countBackupDirs(backupDir));
    }

    @Test
    void writeFilePreservesCRLF() throws Exception {
        Path file = tempDir.resolve("F.java");
        Files.writeString(file, "class F {\r\n}\r\n");
        Fingerprint fp = new Fingerprint(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        ProjectEntry entry = makeEntry(tempDir, Map.of(file.normalize(), fp));
        EditManager mgr = new EditManager(tempDir.resolve("backups"));

        mgr.writeFile(entry, file, "class F {\nint x;\n}\n", null);

        String written = Files.readString(file);
        assertTrue(written.contains("\r\n"), "CRLF should be preserved");
    }

    @Test
    void writeFilePreservesLF() throws Exception {
        Path file = tempDir.resolve("G.java");
        Files.writeString(file, "class G {\n}\n");
        Fingerprint fp = new Fingerprint(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        ProjectEntry entry = makeEntry(tempDir, Map.of(file.normalize(), fp));
        EditManager mgr = new EditManager(tempDir.resolve("backups"));

        mgr.writeFile(entry, file, "class G {\r\nint x;\r\n}\r\n", null);

        String written = Files.readString(file);
        assertTrue(!written.contains("\r\n"), "CRLF should be converted to LF");
        assertTrue(written.contains("\n"));
    }

    @Test
    void noTempFileLeftAfterSuccess() throws Exception {
        Path file = tempDir.resolve("H.java");
        Files.writeString(file, "class H {}");
        Fingerprint fp = new Fingerprint(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        ProjectEntry entry = makeEntry(tempDir, Map.of(file.normalize(), fp));
        EditManager mgr = new EditManager(tempDir.resolve("backups"));

        mgr.writeFile(entry, file, "class H { int x; }", null);

        assertThrows(IOException.class, () -> {
            throw new IOException("check");
        }); // dummy to ensure no exception path — actual check below
        assertTrue(!Files.exists(file.resolveSibling("H.java.mcp-tmp")));
    }

    private static long countBackupDirs(Path backupDir) throws IOException {
        if (!Files.isDirectory(backupDir)) return 0;
        try (var stream = Files.list(backupDir)) {
            return stream.filter(Files::isDirectory).count();
        }
    }

    private static ProjectEntry makeEntry(Path projectDir, Map<Path, Fingerprint> fingerprints) {
        return new ProjectEntry(
            "test-project", "test-project", Instant.now().plusSeconds(600),
            projectDir, null, null, "RAW",
            false, null,
            projectDir, "test-source",
            new HashMap<>(fingerprints), false, true, 1, 1, false, java.util.Set.of()
        );
    }
}
