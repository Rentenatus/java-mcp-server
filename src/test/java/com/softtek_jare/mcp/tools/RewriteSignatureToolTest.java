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
 * Tests for {@link RewriteSignatureTool}.
 *
 * @author Janusch Rentenatus
 */
class RewriteSignatureToolTest {

    @TempDir
    Path tempDir;
    private ProjectManager mgr;
    private EditManager editMgr;
    private RewriteSignatureTool tool;
    private Path srcDir;

    @BeforeEach
    void setup() throws Exception {
        mgr = new ProjectManager();
        editMgr = new EditManager(tempDir.resolve("backups"));
        tool = new RewriteSignatureTool(mgr, editMgr);
        srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
    }

    @Test
    void unchangedSignatureInsertsNoTodoMarkers() throws Exception {
        Path calc = srcDir.resolve("Calc.java");
        Files.writeString(calc, """
            class Calc {
                int add(int a, int b) {
                    return a + b;
                }
            }
            """);
        Path caller = srcDir.resolve("Caller.java");
        Files.writeString(caller, """
            class Caller {
                int use() {
                    return new Calc().add(1, 2);
                }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        // Rewrite to the SAME signature, with caller-update mode.
        CallToolResult result = tool.handle(null, req(
                entry.name(), "Calc", "add", "int", "int a, int b", "signature_and_callers"));

        assertFalse(result.isError());
        assertTrue(result.content().toString().contains("unchanged"),
                "should report signature unchanged, got: " + result.content());
        // Caller file must NOT receive a spurious TODO marker.
        assertFalse(Files.readString(caller).contains("TODO"),
                "no TODO marker expected for unchanged signature, got:\n" + Files.readString(caller));
        mgr.remove(entry.name());
    }

    @Test
    void changedSignatureInsertsTodoMarkersAtCallers() throws Exception {
        Path calc = srcDir.resolve("Calc.java");
        Files.writeString(calc, """
            class Calc {
                int add(int a, int b) {
                    return a + b;
                }
            }
            """);
        Path caller = srcDir.resolve("Caller.java");
        Files.writeString(caller, """
            class Caller {
                int use() {
                    return new Calc().add(1, 2);
                }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, req(
                entry.name(), "Calc", "add", "int", "int a, int b, int c", "signature_and_callers"));

        assertFalse(result.isError());
        assertTrue(Files.readString(caller).contains("TODO"),
                "TODO marker expected at caller for changed signature");
        mgr.remove(entry.name());
    }

    @Test
    void selfCallInDeclaringClassGetsTodoMarker() throws Exception {
        Path calc = srcDir.resolve("Calc.java");
        Files.writeString(calc, """
            class Calc {
                int add(int a, int b) {
                    return a + b;
                }
                int rec() {
                    return add(1, 2);
                }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, req(
                entry.name(), "Calc", "add", "int", "int a, int b, int c", "signature_and_callers"));

        assertFalse(result.isError());
        String written = Files.readString(calc);
        // The self-call in rec() must be flagged, but the declaration line must
        // not be corrupted with a TODO marker.
        assertTrue(written.contains("TODO"),
                "self-call in declaring class should get a TODO marker, got:\n" + written);
        long declOccurrences = written.lines()
                .filter(l -> l.contains("int add(int a, int b, int c)"))
                .count();
        assertTrue(declOccurrences == 1, "declaration must remain intact, got:\n" + written);
        mgr.remove(entry.name());
    }

    private static CallToolRequest req(String name, String className, String methodName,
            String newReturnType, String newParameters, String mode) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("className", className);
        args.put("methodName", methodName);
        if (newReturnType != null) args.put("newReturnType", newReturnType);
        if (newParameters != null) args.put("newParameters", newParameters);
        if (mode != null) args.put("mode", mode);
        return new CallToolRequest("rewrite_signature", args);
    }
}
