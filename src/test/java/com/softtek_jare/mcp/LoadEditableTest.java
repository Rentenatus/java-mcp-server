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

package com.softtek_jare.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.softtek_jare.mcp.model.ProjectEntry;

/**
 * Tests for the {@code editable} flag in {@link ProjectManager#load}.
 *
 * @author Janusch Rentenatus
 */
class LoadEditableTest {

    @TempDir
    Path tempDir;

    @Test
    void localPathDefaultsEditableTrue() throws Exception {
        setupMinimalProject(tempDir);
        ProjectManager mgr = new ProjectManager();
        ProjectEntry entry = mgr.load(tempDir.toString(), null, null, true, null);
        assertTrue(entry.editable());
        mgr.remove(entry.name());
    }

    @Test
    void editableFalseOverridesToLocalPath() throws Exception {
        setupMinimalProject(tempDir);
        ProjectManager mgr = new ProjectManager();
        ProjectEntry entry = mgr.load(tempDir.toString(), null, null, true, false);
        assertFalse(entry.editable());
        mgr.remove(entry.name());
    }

    @Test
    void editableTrueExplicitlyOverrides() throws Exception {
        setupMinimalProject(tempDir);
        ProjectManager mgr = new ProjectManager();
        ProjectEntry entry = mgr.load(tempDir.toString(), null, null, true, true);
        assertTrue(entry.editable());
        mgr.remove(entry.name());
    }

    private static void setupMinimalProject(Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Dummy.java"), "class Dummy {}");
    }
}
