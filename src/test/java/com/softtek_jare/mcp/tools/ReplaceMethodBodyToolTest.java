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
 * Tests for {@link ReplaceMethodBodyTool}.
 *
 * @author Janusch Rentenatus
 */
class ReplaceMethodBodyToolTest {

    @TempDir
    Path tempDir;
    private ProjectManager mgr;
    private EditManager editMgr;
    private ReplaceMethodBodyTool tool;
    private Path srcDir;

    @BeforeEach
    void setup() throws Exception {
        mgr = new ProjectManager();
        editMgr = new EditManager(tempDir.resolve("backups"));
        tool = new ReplaceMethodBodyTool(mgr, editMgr);
        srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
    }

    @Test
    void replaceBodySingleMethod() throws Exception {
        Path file = srcDir.resolve("Calc.java");
        Files.writeString(file, """
            class Calc {
                int compute() {
                    return 1 + 1;
                }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, mockRequest(
                entry.name(), "Calc", "compute", null, "return 42;"));

        assertFalse(result.isError());
        String written = Files.readString(file);
        assertTrue(written.contains("42"));
        // The method signature must survive the body replacement.
        assertTrue(written.contains("int compute()"));
        // The class closing brace must still be present and exactly once after the body.
        long classBraces = written.lines().filter(l -> l.trim().equals("}")).count();
        assertTrue(classBraces >= 2);
        mgr.remove(entry.name());
    }

    @Test
    void replaceBodyWithSignature() throws Exception {
        Path file = srcDir.resolve("Adder.java");
        Files.writeString(file, """
            class Adder {
                int add(int a, int b) {
                    return a + b;
                }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, mockRequest(
                entry.name(), "Adder", "add", "int, int", "return a * b;"));

        assertFalse(result.isError());
        assertTrue(Files.readString(file).contains("a * b"));
        mgr.remove(entry.name());
    }

    @Test
    void methodNotFound() throws Exception {
        Path file = srcDir.resolve("X.java");
        Files.writeString(file, "class X { int getX() { return 0; } }");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, mockRequest(
                entry.name(), "X", "nonExistent", null, "return 0;"));

        assertTrue(result.isError());
        mgr.remove(entry.name());
    }

    @Test
    void ambiguousMethodWithoutSignature() throws Exception {
        Path file = srcDir.resolve("Overload.java");
        Files.writeString(file, """
            class Overload {
                int doWork(int x) { return x; }
                int doWork(int x, int y) { return x + y; }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, mockRequest(
                entry.name(), "Overload", "doWork", null, "return 0;"));

        assertTrue(result.isError());
        mgr.remove(entry.name());
    }

    @Test
    void unresolvedTypeBlocks() throws Exception {
        Path file = srcDir.resolve("Y.java");
        Files.writeString(file, "class Y { int getValue() { return 0; } }");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, mockRequest(
                entry.name(), "Y", "getValue", null, "LocalDateTime now = LocalDateTime.now(); return 0;"));

        assertTrue(result.isError());
        mgr.remove(entry.name());
    }

    @Test
    void replaceBodyGenericParamSignature() throws Exception {
        Path file = srcDir.resolve("Gen.java");
        Files.writeString(file, """
            import java.util.List;
            class Gen {
                void apply(List<String> items) {
                    System.out.println(items);
                }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, mockRequest(
                entry.name(), "Gen", "apply", "List<String>", "return;"));

        assertFalse(result.isError());
        String written = Files.readString(file);
        assertTrue(written.contains("void apply(List<String> items)"));
        assertTrue(written.contains("return;"));
        mgr.remove(entry.name());
    }

    private static CallToolRequest mockRequest(String name, String className,
            String methodName, String signature, String newBody) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("className", className);
        args.put("methodName", methodName);
        if (signature != null) args.put("signature", signature);
        args.put("newBody", newBody);
        return new CallToolRequest("replace_method_body", args);
    }
}
