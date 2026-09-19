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
 * Tests for {@link RemoveMemberTool}.
 *
 * @author Janusch Rentenatus
 */
class RemoveMemberToolTest {

    @TempDir
    Path tempDir;
    private ProjectManager mgr;
    private EditManager editMgr;
    private RemoveMemberTool tool;
    private Path srcDir;

    @BeforeEach
    void setup() throws Exception {
        mgr = new ProjectManager();
        editMgr = new EditManager(tempDir.resolve("backups"));
        tool = new RemoveMemberTool(mgr, editMgr);
        srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
    }

    @Test
    void safeModeRemovesUnreferencedMethod() throws Exception {
        Path file = srcDir.resolve("X.java");
        Files.writeString(file, "class X {\n  void unused() {}\n  int getValue() { return 0; }\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, req(entry.name(), "X", "unused", "method", "safe"));

        assertFalse(result.isError());
        assertFalse(Files.readString(file).contains("unused"));
        mgr.remove(entry.name());
    }

    @Test
    void safeModeRefusesReferencedMethod() throws Exception {
        Path file = srcDir.resolve("Y.java");
        Files.writeString(file, "class Y {\n  int getVal() { return 42; }\n  int caller() { return getVal(); }\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, req(entry.name(), "Y", "getVal", "method", "safe"));

        assertTrue(result.isError());
        assertTrue(result.content().toString().contains("reference"));
        mgr.remove(entry.name());
    }

    @Test
    void hardModeRemovesAndWarnsDangling() throws Exception {
        Path file = srcDir.resolve("Z.java");
        Files.writeString(file, "class Z {\n  int getVal() { return 42; }\n  int caller() { return getVal(); }\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, req(entry.name(), "Z", "getVal", "method", "hard"));

        assertFalse(result.isError());
        assertTrue(result.content().toString().contains("Dangling"));
        mgr.remove(entry.name());
    }

    @Test
    void safeModeRemovesUnreferencedField() throws Exception {
        Path file = srcDir.resolve("Cfg.java");
        Files.writeString(file, "class Cfg {\n  int timeout = 5000;\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, req(entry.name(), "Cfg", "timeout", "field", "safe"));

        assertFalse(result.isError());
        assertFalse(Files.readString(file).contains("timeout"));
        mgr.remove(entry.name());
    }

    @Test
    void hardModeRemovesOneOfMultiFieldDeclaration() throws Exception {
        // "int x, y;" — removing x must not delete y.
        Path file = srcDir.resolve("E.java");
        Files.writeString(file, "class E {\n    int x, y;\n    int z;\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, req(entry.name(), "E", "x", "field", "hard"));

        assertFalse(result.isError());
        String written = Files.readString(file);
        assertFalse(written.contains("int x"));
        assertTrue(written.contains("int y"));
        assertTrue(written.contains("int z"));
        mgr.remove(entry.name());
    }

    @Test
    void hardModeRemovesInitializedFieldFromMultiDeclaration() throws Exception {
        Path file = srcDir.resolve("F.java");
        Files.writeString(file, "class F {\n    int a = 1, b = 2;\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, req(entry.name(), "F", "b", "field", "hard"));

        assertFalse(result.isError());
        String written = Files.readString(file);
        assertTrue(written.contains("int a = 1"));
        assertFalse(written.contains("b = 2"));
        mgr.remove(entry.name());
    }

    @Test
    void overloadedMethodErrorsWithoutSignature() throws Exception {
        Path file = srcDir.resolve("Over.java");
        Files.writeString(file, "class Over {\n  int process(int x) { return x; }\n  String process(String s) { return s; }\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, req(entry.name(), "Over", "process", "method", "hard"));

        assertTrue(result.isError());
        assertTrue(result.content().toString().contains("Multiple methods"));
        mgr.remove(entry.name());
    }

    @Test
    void overloadedMethodRemovedWithSignature() throws Exception {
        Path file = srcDir.resolve("Over2.java");
        Files.writeString(file, "class Over2 {\n  int process(int x) { return x; }\n  String process(String s) { return s; }\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, reqWithSig(entry.name(), "Over2", "process", "method", "hard", "int"));

        assertFalse(result.isError());
        String written = Files.readString(file);
        assertFalse(written.contains("process(int"));
        assertTrue(written.contains("process(String"));
        mgr.remove(entry.name());
    }

    private static CallToolRequest req(String name, String className, String memberName,
            String scope, String mode) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("className", className);
        args.put("memberName", memberName);
        args.put("scope", scope);
        args.put("mode", mode);
        return new CallToolRequest("remove_member", args);
    }

    private static CallToolRequest reqWithSig(String name, String className, String memberName,
            String scope, String mode, String signature) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("className", className);
        args.put("memberName", memberName);
        args.put("scope", scope);
        args.put("mode", mode);
        args.put("signature", signature);
        return new CallToolRequest("remove_member", args);
    }
}
