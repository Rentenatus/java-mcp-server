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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.softtek_jare.mcp.model.Fingerprint;
import com.softtek_jare.mcp.model.ProjectEntry;
import com.softtek_jare.mcp.tools.BaseJavaTool.DirtyCheckResult;

/**
 * Tests for dirty-check helpers in {@link BaseJavaTool}: {@code formatExpiredWarning},
 * {@code formatDirtyWarning}, {@code DirtyCheckResult}, {@code checkDirtyFullScan}.
 *
 * @author Janusch Rentenatus
 */
class DirtyCheckTest {

    // --- formatExpiredWarning ---

    @Test
    void formatExpiredWarningEmptyListReturnsEmpty() {
        assertEquals("", BaseJavaTool.formatExpiredWarning(List.of()));
    }

    @Test
    void formatExpiredWarningNullReturnsEmpty() {
        assertEquals("", BaseJavaTool.formatExpiredWarning(null));
    }

    @Test
    void formatExpiredWarningSingleProject() {
        String warning = BaseJavaTool.formatExpiredWarning(List.of("my-project"));
        assertNotNull(warning);
        assertTrue(warning.contains("my-project"));
        assertTrue(warning.contains("Expired projects"));
        assertTrue(warning.contains("reload_java_project"));
    }

    @Test
    void formatExpiredWarningMultipleProjects() {
        String warning = BaseJavaTool.formatExpiredWarning(List.of("proj-a", "proj-b", "proj-c"));
        assertTrue(warning.contains("proj-a"));
        assertTrue(warning.contains("proj-b"));
        assertTrue(warning.contains("proj-c"));
    }

    // --- DirtyCheckResult ---

    @Test
    void dirtyCheckResultIsDirtyWhenChanged() {
        DirtyCheckResult r = new DirtyCheckResult(1, List.of("file.java"), List.of(), List.of());
        assertTrue(r.isDirty());
    }

    @Test
    void dirtyCheckResultIsDirtyWhenDeleted() {
        DirtyCheckResult r = new DirtyCheckResult(1, List.of(), List.of("deleted.java"), List.of());
        assertTrue(r.isDirty());
    }

    @Test
    void dirtyCheckResultIsDirtyWhenNew() {
        DirtyCheckResult r = new DirtyCheckResult(1, List.of(), List.of(), List.of("new.java"));
        assertTrue(r.isDirty());
    }

    @Test
    void dirtyCheckResultIsCleanWhenAllEmpty() {
        DirtyCheckResult r = new DirtyCheckResult(5, List.of(), List.of(), List.of());
        assertFalse(r.isDirty());
    }

    // --- formatDirtyWarning ---

    @Test
    void formatDirtyWarningCleanReturnsEmpty() {
        DirtyCheckResult clean = new DirtyCheckResult(1, List.of(), List.of(), List.of());
        assertEquals("", BaseJavaTool.formatDirtyWarning(clean));
    }

    @Test
    void formatDirtyWarningDirtyContainsFiles() {
        DirtyCheckResult dirty = new DirtyCheckResult(2, List.of("a.java", "b.java"), List.of(), List.of());
        String warning = BaseJavaTool.formatDirtyWarning(dirty);
        assertTrue(warning.contains("a.java"));
        assertTrue(warning.contains("b.java"));
        assertTrue(warning.contains("reload_java_project"));
    }

    @Test
    void formatDirtyWarningCapsAtTenFiles() {
        java.util.List<String> files = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) files.add("file" + i + ".java");
        DirtyCheckResult dirty = new DirtyCheckResult(15, files, List.of(), List.of());
        String warning = BaseJavaTool.formatDirtyWarning(dirty);
        assertTrue(warning.contains("file0.java"));
        assertTrue(warning.contains("file9.java"));
        assertFalse(warning.contains("file14.java"));
        assertTrue(warning.contains("5 more"));
    }

    // --- checkDirtyFullScan (with temp files) ---

    @Test
    void checkDirtyFullScanCleanWhenNothingChanged(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("A.java");
        Files.writeString(file, "class A {}");

        Map<Path, Fingerprint> fingerprints = new HashMap<>();
        fingerprints.put(file.normalize(), new Fingerprint(
            Files.getLastModifiedTime(file).toMillis(), Files.size(file)));

        ProjectEntry entry = makeMinimalEntry(tempDir, fingerprints);

        DirtyCheckResult result = BaseJavaTool.checkDirtyFullScan(entry);
        assertFalse(result.isDirty());
        assertEquals(1, result.filesChecked());
    }

    @Test
    void checkDirtyFullScanDetectsChangedFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("B.java");
        Files.writeString(file, "class B {}");

        Map<Path, Fingerprint> fingerprints = new HashMap<>();
        fingerprints.put(file.normalize(), new Fingerprint(
            Files.getLastModifiedTime(file).toMillis(), Files.size(file)));

        // Wait a moment then modify the file
        Thread.sleep(50);
        Files.writeString(file, "class B { int x; }");

        ProjectEntry entry = makeMinimalEntry(tempDir, fingerprints);

        DirtyCheckResult result = BaseJavaTool.checkDirtyFullScan(entry);
        assertTrue(result.isDirty());
        assertEquals(1, result.changedFiles().size());
        assertTrue(result.changedFiles().get(0).contains("B.java"));
    }

    @Test
    void checkDirtyFullScanDetectsDeletedFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("C.java");
        Files.writeString(file, "class C {}");

        Map<Path, Fingerprint> fingerprints = new HashMap<>();
        fingerprints.put(file.normalize(), new Fingerprint(
            Files.getLastModifiedTime(file).toMillis(), Files.size(file)));

        Files.delete(file);

        ProjectEntry entry = makeMinimalEntry(tempDir, fingerprints);

        DirtyCheckResult result = BaseJavaTool.checkDirtyFullScan(entry);
        assertTrue(result.isDirty());
        assertEquals(1, result.deletedFiles().size());
    }

    @Test
    void checkDirtyFullScanDetectsNewFile(@TempDir Path tempDir) throws Exception {
        // Empty dir, no fingerprints
        ProjectEntry entry = makeMinimalEntry(tempDir, new HashMap<>());

        // Create a new file
        Files.writeString(tempDir.resolve("New.java"), "class New {}");

        DirtyCheckResult result = BaseJavaTool.checkDirtyFullScan(entry);
        assertTrue(result.isDirty());
        assertEquals(1, result.newFiles().size());
        assertTrue(result.newFiles().get(0).contains("New.java"));
    }

    @Test
    void checkDirtyFullScanHandlesNonExistentDir() {
        Map<Path, Fingerprint> fingerprints = new HashMap<>();
        ProjectEntry entry = makeMinimalEntry(Path.of("/nonexistent/path"), fingerprints);

        DirtyCheckResult result = BaseJavaTool.checkDirtyFullScan(entry);
        assertFalse(result.isDirty());
        assertEquals(0, result.filesChecked());
    }

    @Test
    void checkDirtyFullScanDetectsMixedChanges(@TempDir Path tempDir) throws Exception {
        Path fileA = tempDir.resolve("A.java");
        Path fileB = tempDir.resolve("B.java");
        Files.writeString(fileA, "class A {}");
        Files.writeString(fileB, "class B {}");

        Map<Path, Fingerprint> fingerprints = new HashMap<>();
        fingerprints.put(fileA.normalize(), new Fingerprint(
            Files.getLastModifiedTime(fileA).toMillis(), Files.size(fileA)));
        fingerprints.put(fileB.normalize(), new Fingerprint(
            Files.getLastModifiedTime(fileB).toMillis(), Files.size(fileB)));

        // Modify A, delete B, create C
        Thread.sleep(50);
        Files.writeString(fileA, "class A { int x; }");
        Files.delete(fileB);
        Files.writeString(tempDir.resolve("C.java"), "class C {}");

        ProjectEntry entry = makeMinimalEntry(tempDir, fingerprints);

        DirtyCheckResult result = BaseJavaTool.checkDirtyFullScan(entry);
        assertTrue(result.isDirty());
        assertEquals(1, result.changedFiles().size());
        assertEquals(1, result.deletedFiles().size());
        assertEquals(1, result.newFiles().size());
    }

    // --- Helper ---

    /**
     * Creates a minimal ProjectEntry for testing dirty checks. Only the fields
     * needed by checkDirtyFullScan are populated.
     */
    private static ProjectEntry makeMinimalEntry(Path originalProjectDir, Map<Path, Fingerprint> fingerprints) {
        return new ProjectEntry(
            "test-project", "test-project", java.time.Instant.now().plusSeconds(600),
            originalProjectDir, null, null, "RAW",
            false, null,
            originalProjectDir, "test-source",
            fingerprints, false
        );
    }
}
