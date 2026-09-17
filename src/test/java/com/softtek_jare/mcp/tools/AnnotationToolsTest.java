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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.softtek_jare.mcp.ProjectManager;
import com.softtek_jare.mcp.edit.EditManager;
import com.softtek_jare.mcp.model.ProjectEntry;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

/**
 * Tests for {@link AddAnnotationTool}, {@link RemoveAnnotationTool}, {@link EditAnnotationTool}.
 *
 * @author Janusch Rentenatus
 */
class AnnotationToolsTest {

    @TempDir
    Path tempDir;
    private ProjectManager mgr;
    private EditManager editMgr;
    private AddAnnotationTool addAnno;
    private RemoveAnnotationTool removeAnno;
    private EditAnnotationTool editAnno;
    private Path srcDir;

    @BeforeEach
    void setup() throws Exception {
        mgr = new ProjectManager();
        editMgr = new EditManager(tempDir.resolve("backups"));
        addAnno = new AddAnnotationTool(mgr, editMgr);
        removeAnno = new RemoveAnnotationTool(mgr, editMgr);
        editAnno = new EditAnnotationTool(mgr, editMgr);
        srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
    }

    @Test
    void addAnnotationToClass() throws Exception {
        Path file = srcDir.resolve("Svc.java");
        Files.writeString(file, "class Svc {\n  void handle() {}\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addAnno.handle(null, req(entry.name(), "class",
                "Svc", null, "Deprecated", null));

        assertFalse(result.isError());
        assertTrue(Files.readString(file).contains("@Deprecated"));
        mgr.remove(entry.name());
    }

    @Test
    void addAnnotationToMethod() throws Exception {
        Path file = srcDir.resolve("Ctrl.java");
        Files.writeString(file, "class Ctrl {\n  void handle() {}\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addAnno.handle(null, req(entry.name(), "method",
                "Ctrl", "handle", "Override", null));

        assertFalse(result.isError());
        assertTrue(Files.readString(file).contains("@Override"));
        mgr.remove(entry.name());
    }

    @Test
    void addAnnotationWithAttributes() throws Exception {
        Path file = srcDir.resolve("R.java");
        Files.writeString(file, "class R {\n  void process() {}\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addAnno.handle(null, req(entry.name(), "method",
                "R", "process", "Transactional", "value=true"));

        assertFalse(result.isError());
        assertTrue(Files.readString(file).contains("@Transactional(value=true)"));
        mgr.remove(entry.name());
    }

    @Test
    void removeAnnotationPresent() throws Exception {
        Path file = srcDir.resolve("S.java");
        Files.writeString(file, "@Deprecated\nclass S {\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = removeAnno.handle(null, req(entry.name(), "class",
                "S", null, "Deprecated", null));

        assertFalse(result.isError());
        assertFalse(Files.readString(file).contains("@Deprecated"));
        mgr.remove(entry.name());
    }

    @Test
    void removeAnnotationNotPresent() throws Exception {
        Path file = srcDir.resolve("T.java");
        Files.writeString(file, "class T {\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = removeAnno.handle(null, req(entry.name(), "class",
                "T", null, "Override", null));

        assertTrue(result.isError());
        mgr.remove(entry.name());
    }

    @Test
    void editAnnotationAttributes() throws Exception {
        Path file = srcDir.resolve("U.java");
        Files.writeString(file, "@Transactional(value=false)\nclass U {\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = editAnno.handle(null, reqEdit(entry.name(), "class",
                "U", "Transactional", "value=true"));

        assertFalse(result.isError());
        assertTrue(Files.readString(file).contains("value=true"));
        mgr.remove(entry.name());
    }

    @Test
    void editAnnotationNotPresent() throws Exception {
        Path file = srcDir.resolve("V.java");
        Files.writeString(file, "class V {\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = editAnno.handle(null, reqEdit(entry.name(), "class",
                "V", "Override", "value=true"));

        assertTrue(result.isError());
        mgr.remove(entry.name());
    }

    private static CallToolRequest req(String name, String targetType, String className,
            String targetName, String annotation, String attributes) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("targetType", targetType);
        args.put("className", className);
        if (targetName != null) args.put("targetName", targetName);
        args.put("annotation", annotation);
        if (attributes != null) args.put("attributes", attributes);
        return new CallToolRequest("add_annotation", args);
    }

    private static CallToolRequest reqEdit(String name, String targetType, String className,
            String annotation, String newAttributes) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("targetType", targetType);
        args.put("className", className);
        args.put("annotation", annotation);
        args.put("newAttributes", newAttributes);
        return new CallToolRequest("edit_annotation", args);
    }
}
