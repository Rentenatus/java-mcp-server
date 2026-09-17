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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.softtek_jare.mcp.ProjectManager;
import com.softtek_jare.mcp.model.Fingerprint;
import com.softtek_jare.mcp.model.ProjectEntry;

/**
 * Tests for the transaction layer in {@link EditManager}.
 *
 * @author Janusch Rentenatus
 */
class TransactionTest {

    @TempDir
    Path tempDir;
    private EditManager mgr;
    private Path file;

    @BeforeEach
    void setup() throws Exception {
        mgr = new EditManager(tempDir.resolve("backups"));
        file = tempDir.resolve("A.java");
        Files.writeString(file, "class A {\nint x;\n}\n");
    }

    private ProjectEntry makeEntry() throws Exception {
        Fingerprint fp = new Fingerprint(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        return new ProjectEntry(
            "test", "test", Instant.now().plusSeconds(600),
            tempDir, null, null, "RAW",
            false, null, tempDir, "test",
            new HashMap<>(Map.of(file.normalize(), fp)), false, true, 1, 1);
    }

    @Test
    void beginTransactionAccumulatesWithoutWriting() throws Exception {
        ProjectEntry entry = makeEntry();
        mgr.beginTransaction();
        assertTrue(mgr.isInTransaction());

        mgr.writeFile(entry, file, "class A { int y; }", null);

        // File on disk should NOT have changed yet
        assertTrue(Files.readString(file).contains("int x"));
        assertFalse(Files.readString(file).contains("int y"));
        assertEquals(1, mgr.getPendingChangeCount());
    }

    @Test
    void commitWritesAllChanges() throws Exception {
        ProjectEntry entry = makeEntry();
        mgr.beginTransaction();
        mgr.writeFile(entry, file, "class A { int y; }", null);

        boolean success = mgr.commitTransaction();

        assertTrue(success);
        assertFalse(mgr.isInTransaction());
        assertTrue(Files.readString(file).contains("int y"));
    }

    @Test
    void rollbackDiscardsChanges() throws Exception {
        ProjectEntry entry = makeEntry();
        mgr.beginTransaction();
        mgr.writeFile(entry, file, "class A { int y; }", null);

        mgr.rollbackTransaction();

        assertFalse(mgr.isInTransaction());
        assertTrue(Files.readString(file).contains("int x"));
        assertFalse(Files.readString(file).contains("int y"));
    }

    @Test
    void commitWithoutTransactionReturnsTrue() throws Exception {
        boolean success = mgr.commitTransaction();
        assertTrue(success);
    }

    @Test
    void rollbackWithoutTransactionIsNoop() throws Exception {
        mgr.rollbackTransaction();
        assertFalse(mgr.isInTransaction());
    }

    @Test
    void editLogAccumulates() throws Exception {
        mgr.logEdit("rename_symbol: foo -> bar");
        mgr.logEdit("add_method: getValue");

        var log = mgr.getEditLog();
        assertEquals(2, log.size());
        assertTrue(log.get(0).contains("rename"));
        assertTrue(log.get(1).contains("add_method"));
    }

    @Test
    void editLogEmptyByDefault() {
        assertTrue(mgr.getEditLog().isEmpty());
    }
}
