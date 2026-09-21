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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * Tests for {@link EditLineTool}.
 *
 * @author Janusch Rentenatus
 */
class EditLineToolTest {

    @TempDir
    Path tempDir;
    private ProjectManager mgr;
    private EditManager editMgr;
    private EditLineTool tool;
    private Path srcDir;

    @BeforeEach
    void setup() throws Exception {
        mgr = new ProjectManager();
        editMgr = new EditManager(tempDir.resolve("backups"));
        tool = new EditLineTool(mgr, editMgr);
        srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
    }

    @Test
    void replaceSingleLine() throws Exception {
        Path file = srcDir.resolve("A.java");
        Files.writeString(file, "class A {\nint x;\nint y;\n}\n");
        ProjectEntry entry = loadProject(file);

        CallToolResult result = tool.handle(
                null, mockRequest(entry.name(), file.toString(), 2, "int z = 42;"));

        assertFalse(result.isError());
        String[] lines = Files.readString(file).split("\n");
        assertEquals("int z = 42;", lines[1].trim());
    }

    @Test
    void replaceLineInCRLFFile() throws Exception {
        Path file = srcDir.resolve("B.java");
        Files.writeString(file, "class B {\r\nint x;\r\n}\r\n");
        ProjectEntry entry = loadProject(file);

        CallToolResult result = tool.handle(
                null, mockRequest(entry.name(), file.toString(), 2, "int y;"));

        assertFalse(result.isError());
        String written = Files.readString(file);
        assertTrue(written.contains("\r\n"), "CRLF should be preserved");
        assertTrue(written.contains("int y;"));
    }

    @Test
    void lineNumberOutOfRange() throws Exception {
        Path file = srcDir.resolve("C.java");
        Files.writeString(file, "class C {}\n");
        ProjectEntry entry = loadProject(file);

        CallToolResult result = tool.handle(
                null, mockRequest(entry.name(), file.toString(), 99, "int x;"));

        assertFalse(result.isError());
        assertTrue(result.content().toString().contains("isDomainError"));
    }

    @Test
    void phantomTrailingLineNotAddressable() throws Exception {
        // File "class C {}\n" has 1 content line + a trailing newline. The
        // trailing newline must not create a replaceable phantom "line 2".
        Path file = srcDir.resolve("C2.java");
        Files.writeString(file, "class C {}\n");
        ProjectEntry entry = loadProject(file);

        CallToolResult result = tool.handle(
                null, mockRequest(entry.name(), file.toString(), 2, "int x;"));

        assertFalse(result.isError());
        assertTrue(result.content().toString().contains("isDomainError"));
        // Trailing newline must be preserved (not dropped by a phantom edit).
        String written = Files.readString(file);
        assertTrue(written.endsWith("\n"), "trailing newline must be preserved");
        assertFalse(written.contains("int x;"));
    }

    @Test
    void replaceLastContentLinePreservesTrailingNewline() throws Exception {
        Path file = srcDir.resolve("C3.java");
        Files.writeString(file, "class C {\nint x;\n}\n");
        ProjectEntry entry = loadProject(file);

        CallToolResult result = tool.handle(
                null, mockRequest(entry.name(), file.toString(), 3, "} // closed"));

        assertFalse(result.isError());
        String written = Files.readString(file);
        assertTrue(written.endsWith("\n"), "trailing newline must be preserved");
        assertTrue(written.contains("} // closed"));
    }

    @Test
    void lineNumberZero() throws Exception {
        Path file = srcDir.resolve("D.java");
        Files.writeString(file, "class D {}\n");
        ProjectEntry entry = loadProject(file);

        CallToolResult result = tool.handle(
                null, mockRequest(entry.name(), file.toString(), 0, "int x;"));

        assertFalse(result.isError());
        assertTrue(result.content().toString().contains("isDomainError"));
    }

    @Test
    void crInNewContentStripped() throws Exception {
        Path file = srcDir.resolve("E.java");
        Files.writeString(file, "class E {\nint x;\n}\n");
        ProjectEntry entry = loadProject(file);

        CallToolResult result = tool.handle(
                null, mockRequest(entry.name(), file.toString(), 2, "int y;\r\n"));

        assertFalse(result.isError());
        String written = Files.readString(file);
        assertFalse(written.contains("int y;\r"));
        assertTrue(written.contains("int y;"));
        // A trailing \n in newContent must not create a spurious blank line.
        assertFalse(written.contains("int y;\n\n"), "trailing newline must not create a blank line");
    }

    @Test
    void trailingNewlineInNewContentStripped() throws Exception {
        // newContent with a trailing \n (LF only, no CR) must also not create
        // a blank line — the line ending is added by the join, not by the caller.
        Path file = srcDir.resolve("E2.java");
        Files.writeString(file, "class E {\nint x;\n}\n");
        ProjectEntry entry = loadProject(file);

        CallToolResult result = tool.handle(
                null, mockRequest(entry.name(), file.toString(), 2, "int y;\n"));

        assertFalse(result.isError());
        String written = Files.readString(file);
        assertFalse(written.contains("int y;\n\n"), "trailing LF must not create a blank line");
        assertTrue(written.contains("int y;\n}\n"));
    }

    @Test
    void nonEditableProjectRejected() throws Exception {
        Path file = srcDir.resolve("F.java");
        Files.writeString(file, "class F {}\n");
        ProjectEntry entry = mgr.load(tempDir.toString(), "readOnly", null, true, false);

        assertThrows(IllegalArgumentException.class, () ->
            tool.handle(null, mockRequest("readOnly", file.toString(), 1, "class F { int x; }")));

        mgr.remove(entry.name());
    }

    @Test
    void relativePathResolvedAgainstSourceRoot() throws Exception {
        // Maven-style layout: project root contains src/main/java/com/example/Foo.java.
        // A relative path like "com/example/Foo.java" must resolve against the
        // source root (src/main/java), not the project root.
        Path projRoot = tempDir.resolve("mavenproj");
        Path srcRoot = projRoot.resolve("src/main/java/com/example");
        Files.createDirectories(srcRoot);
        Path file = srcRoot.resolve("Foo.java");
        Files.writeString(file, "package com.example;\n\nclass Foo {\n    int x;\n}\n");
        // Minimal pom.xml so the loader detects MAVEN
        Files.writeString(projRoot.resolve("pom.xml"),
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">"
                + "<modelVersion>4.0.0</modelVersion>"
                + "<groupId>test</groupId><artifactId>test</artifactId><version>1</version>"
                + "</project>");
        ProjectEntry entry = mgr.load(projRoot.toString(), "mavenproj", null, true, true);

        CallToolResult result = tool.handle(
                null, mockRequest("mavenproj", "com/example/Foo.java", 4, "    int z = 99;"));

        assertFalse(result.isError(), () -> result.content().toString());
        String written = Files.readString(file);
        assertTrue(written.contains("int z = 99;"), "relative path edit must reach the file:\n" + written);
        mgr.remove("mavenproj");
    }

    private ProjectEntry loadProject(Path file) throws Exception {
        Path projDir = file.getParent().getParent();
        ProjectEntry entry = mgr.load(projDir.toString(), null, null, true, true);
        mgr.remove(entry.name());
        entry = mgr.load(file.getParent().toString(), null, null, true, true);
        return entry;
    }

    private static CallToolRequest mockRequest(String name, String file,
            int lineNumber, String newContent) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("file", file);
        args.put("lineNumber", lineNumber);
        args.put("newContent", newContent);
        return new CallToolRequest("edit_line", args);
    }
}
