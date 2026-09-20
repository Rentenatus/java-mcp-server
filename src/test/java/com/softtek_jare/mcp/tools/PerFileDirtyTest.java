package com.softtek_jare.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.softtek_jare.mcp.ProjectManager;
import com.softtek_jare.mcp.edit.EditManager;
import com.softtek_jare.mcp.model.ProjectEntry;

/**
 * Per-file dirty tracking: replaces the former global {@code boolean modelDirty}
 * with {@code Set<Path> dirtyFiles}. An edit on file A marks only A dirty;
 * a subsequent edit on file B can proceed without reload, because B is clean.
 * This is the central behaviour change from the per-file-dirty plan.
 */
class PerFileDirtyTest {

    @TempDir
    Path tempDir;
    private ProjectManager mgr;
    private EditManager editMgr;
    private Path srcDir;

    @BeforeEach
    void setup() throws Exception {
        mgr = new ProjectManager();
        editMgr = new EditManager(tempDir.resolve("backups"));
        srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
    }

    /** Fresh entry: dirtyFiles empty, modelDirty() derived false. */
    @Test
    void freshEntryHasEmptyDirtyFiles() throws Exception {
        ProjectEntry entry = loadTwoFiles();
        assertTrue(entry.dirtyFiles().isEmpty(), "dirtyFiles should be empty on load");
        assertFalse(entry.modelDirty(), "modelDirty() should be false when dirtyFiles is empty");
        assertFalse(entry.isModelDirty());
    }

    /** Edit on file A marks only A dirty; file B stays clean. */
    @Test
    void editOnFileAOnlyMarksADirty() throws Exception {
        ProjectEntry entry = loadTwoFiles();
        Path alpha = srcDir.resolve("Alpha.java");
        Path beta = srcDir.resolve("Beta.java");

        ProjectEntry updated = editMgr.writeFile(entry, alpha, Files.readString(alpha) + "\n// touched\n", null);
        mgr.updateEntry(updated);
        entry = mgr.find(entry.name());

        assertTrue(entry.isFileDirty(alpha), "Alpha should be dirty after edit");
        assertFalse(entry.isFileDirty(beta), "Beta should NOT be dirty — only Alpha was edited");
        assertTrue(entry.modelDirty(), "modelDirty() should be true (Alpha is dirty)");
        assertEquals(1, entry.dirtyFiles().size(), "exactly one file should be dirty");
    }

    /** After edit on A, editing B is still allowed (B is clean) — no global block. */
    @Test
    void editOnBAfterEditOnAStillWrites() throws Exception {
        ProjectEntry entry = loadTwoFiles();
        Path alpha = srcDir.resolve("Alpha.java");
        Path beta = srcDir.resolve("Beta.java");

        ProjectEntry afterA = editMgr.writeFile(entry, alpha, Files.readString(alpha) + "\n// touched A\n", null);
        mgr.updateEntry(afterA);
        // Edit on B — must succeed because B is clean (per-file, not global block).
        // writeFile throws on failure; reaching the assertions means it wrote.
        ProjectEntry after = editMgr.writeFile(afterA, beta, Files.readString(beta) + "\n// touched B\n", null);
        assertTrue(after.isFileDirty(beta));
        assertTrue(after.isFileDirty(alpha), "Alpha should still be dirty");
        assertEquals(2, after.dirtyFiles().size());
    }

    /** reload clears dirtyFiles (the model is freshly parsed). */
    @Test
    void reloadClearsDirtyFiles() throws Exception {
        ProjectEntry entry = loadTwoFiles();
        Path alpha = srcDir.resolve("Alpha.java");

        ProjectEntry updated = editMgr.writeFile(entry, alpha, Files.readString(alpha) + "\n// touched\n", null);
        mgr.updateEntry(updated);
        assertTrue(mgr.find(entry.name()).isFileDirty(alpha));

        ProjectEntry reloaded = mgr.reload(entry.name());
        assertTrue(reloaded.dirtyFiles().isEmpty(), "dirtyFiles must be empty after reload");
        assertFalse(reloaded.modelDirty());
    }

    private ProjectEntry loadTwoFiles() throws Exception {
        Files.writeString(srcDir.resolve("Alpha.java"), "class Alpha {\n    int a;\n}\n");
        Files.writeString(srcDir.resolve("Beta.java"), "class Beta {\n    int b;\n}\n");
        return mgr.load(srcDir.toString(), null, null, true, true);
    }
}
