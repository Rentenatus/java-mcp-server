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
 * Tests for {@link AddMethodTool} and {@link AddFieldTool}.
 *
 * @author Janusch Rentenatus
 */
class AddMethodAddFieldTest {

    @TempDir
    Path tempDir;
    private ProjectManager mgr;
    private EditManager editMgr;
    private AddMethodTool addMethod;
    private AddFieldTool addField;
    private Path srcDir;

    @BeforeEach
    void setup() throws Exception {
        mgr = new ProjectManager();
        editMgr = new EditManager(tempDir.resolve("backups"));
        addMethod = new AddMethodTool(mgr, editMgr);
        addField = new AddFieldTool(mgr, editMgr);
        srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
    }

    @Test
    void addMethodSimple() throws Exception {
        Path file = srcDir.resolve("Box.java");
        Files.writeString(file, "class Box {\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addMethod.handle(null, methodReq(
                entry.name(), "Box", "getValue", "int", null, "public", "return 42;"));

        assertFalse(result.isError());
        assertTrue(Files.readString(file).contains("getValue"));
        assertTrue(Files.readString(file).contains("return 42"));
        mgr.remove(entry.name());
    }

    @Test
    void addMethodErasureClashDetected() throws Exception {
        Path file = srcDir.resolve("C.java");
        Files.writeString(file, "class C { void foo(int x) {} }\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addMethod.handle(null, methodReq(
                entry.name(), "C", "foo", "void", "int y", null, null));

        assertTrue(result.isError());
        assertTrue(result.content().toString().contains("erasure"));
        mgr.remove(entry.name());
    }

    @Test
    void addMethodUnresolvedTypeBlocks() throws Exception {
        Path file = srcDir.resolve("D.java");
        Files.writeString(file, "class D {}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addMethod.handle(null, methodReq(
                entry.name(), "D", "process", "void", null, "public",
                "LocalDateTime now = LocalDateTime.now();"));

        assertTrue(result.isError());
        mgr.remove(entry.name());
    }

    @Test
    void addFieldSimple() throws Exception {
        Path file = srcDir.resolve("Cfg.java");
        Files.writeString(file, "class Cfg {\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addField.handle(null, fieldReq(
                entry.name(), "Cfg", "timeout", "int", "private", "5000"));

        assertFalse(result.isError());
        assertTrue(Files.readString(file).contains("timeout"));
        assertTrue(Files.readString(file).contains("5000"));
        mgr.remove(entry.name());
    }

    @Test
    void addFieldDuplicateDetected() throws Exception {
        Path file = srcDir.resolve("E.java");
        Files.writeString(file, "class E { int x = 0; }\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addField.handle(null, fieldReq(
                entry.name(), "E", "x", "int", null, null));

        assertTrue(result.isError());
        mgr.remove(entry.name());
    }

    @Test
    void addFieldUnresolvedTypeBlocks() throws Exception {
        Path file = srcDir.resolve("F.java");
        Files.writeString(file, "class F {}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addField.handle(null, fieldReq(
                entry.name(), "F", "when", "LocalDateTime", null, null));

        assertTrue(result.isError());
        mgr.remove(entry.name());
    }

    private static CallToolRequest methodReq(String name, String className, String methodName,
            String returnType, String parameters, String modifiers, String body) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("className", className);
        args.put("methodName", methodName);
        args.put("returnType", returnType);
        if (parameters != null) args.put("parameters", parameters);
        if (modifiers != null) args.put("modifiers", modifiers);
        if (body != null) args.put("body", body);
        return new CallToolRequest("add_method", args);
    }

    private static CallToolRequest fieldReq(String name, String className, String fieldName,
            String type, String modifiers, String initializer) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("className", className);
        args.put("fieldName", fieldName);
        args.put("type", type);
        if (modifiers != null) args.put("modifiers", modifiers);
        if (initializer != null) args.put("initializer", initializer);
        return new CallToolRequest("add_field", args);
    }
}
