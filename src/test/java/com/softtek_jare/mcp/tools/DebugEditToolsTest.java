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
import com.softtek_jare.mcp.edit.AutoImportResolver;
import com.softtek_jare.mcp.edit.EditManager;
import com.softtek_jare.mcp.model.ProjectEntry;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import spoon.reflect.declaration.CtType;

/**
 * Debug test that exercises every fix P25-P35 end-to-end with verbose output
 * so the behavior is visible in the test log.
 *
 * @author Janusch Rentenatus
 */
class DebugEditToolsTest {

    @TempDir
    Path tempDir;
    private ProjectManager mgr;
    private EditManager editMgr;
    private Path srcDir;
    private int pass = 0;
    private int fail = 0;

    @BeforeEach
    void setup() throws Exception {
        mgr = new ProjectManager();
        editMgr = new EditManager(tempDir.resolve("backups"));
        srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        pass = 0;
        fail = 0;
    }

    @Test
    void runAllDebugChecks() throws Exception {
        System.out.println("==== DebugEditToolsTest start ====");
        checkP25_stringAndCommentWordsNotResolved();
        checkP26_addMethodIndented();
        checkP27_unchangedSignatureNoTodo();
        checkP28_selfCallInDeclaringClassFlagged();
        checkP31_genericCommaParamErasureClash();
        checkP30_invalidTargetTypeRejected();
        checkP32_errorMessageSaysSignatureOrBody();
        checkP34_annotationInsertedBeforeDeclaration();
        System.out.println("==== DebugEditToolsTest summary: " + pass + " passed, " + fail + " failed ====");
        if (fail > 0) {
            throw new AssertionError(fail + " debug checks failed (see output above)");
        }
    }

    // --- P25: capitalized words in strings/comments are not resolved as types ---

    void checkP25_stringAndCommentWordsNotResolved() throws Exception {
        Files.writeString(srcDir.resolve("X.java"), "class X {}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        CtType<?> type = entry.model().getAllTypes().stream().findFirst().orElseThrow();

        AutoImportResolver resolver = new AutoImportResolver();
        var result = resolver.resolve(entry, type,
                "throw new RuntimeException(\"Failed\"); // TODO: Review this");

        boolean ok = result.unresolvedTypes().isEmpty();
        report("P25 string/comment words not resolved", ok,
                "unresolved=" + result.unresolvedTypes());
        mgr.remove(entry.name());
    }

    // --- P26: AddMethodTool indents the generated body and closing brace ---

    void checkP26_addMethodIndented() throws Exception {
        Path file = srcDir.resolve("Box.java");
        Files.writeString(file, "class Box {\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        AddMethodTool tool = new AddMethodTool(mgr, editMgr);
        CallToolResult r = tool.handle(null, req("add_method", Map.of(
                "name", entry.name(), "className", "Box",
                "methodName", "getValue", "returnType", "int",
                "modifiers", "public", "body", "return 42;")));

        String written = Files.readString(file);
        boolean bodyIndented = written.lines().anyMatch(l -> l.equals("        return 42;"));
        boolean braceIndented = written.lines().anyMatch(l -> l.trim().equals("}") && l.startsWith("    "));
        report("P26 body+brace indented", !r.isError() && bodyIndented && braceIndented,
                "bodyIndented=" + bodyIndented + ", braceIndented=" + braceIndented + "\n" + written);
        mgr.remove(entry.name());
    }

    // --- P27: unchanged signature produces no TODO markers ---

    void checkP27_unchangedSignatureNoTodo() throws Exception {
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

        RewriteSignatureTool tool = new RewriteSignatureTool(mgr, editMgr);
        CallToolResult r = tool.handle(null, req("rewrite_signature", Map.of(
                "name", entry.name(), "className", "Calc", "methodName", "add",
                "newReturnType", "int", "newParameters", "int a, int b",
                "mode", "signature_and_callers")));

        boolean noTodo = !Files.readString(caller).contains("TODO");
        report("P27 no TODO on unchanged signature", !r.isError() && noTodo,
                "content=" + r.content() + ", callerHasTodo=" + !noTodo);
        mgr.remove(entry.name());
    }

    // --- P28: self-call inside the declaring class gets a TODO marker ---

    void checkP28_selfCallInDeclaringClassFlagged() throws Exception {
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

        RewriteSignatureTool tool = new RewriteSignatureTool(mgr, editMgr);
        CallToolResult r = tool.handle(null, req("rewrite_signature", Map.of(
                "name", entry.name(), "className", "Calc", "methodName", "add",
                "newReturnType", "int", "newParameters", "int a, int b, int c",
                "mode", "signature_and_callers")));

        String written = Files.readString(calc);
        boolean hasTodo = written.contains("TODO");
        boolean declIntact = written.lines().filter(l -> l.contains("int add(int a, int b, int c)")).count() == 1;
        report("P28 self-call flagged + declaration intact",
                !r.isError() && hasTodo && declIntact,
                "hasTodo=" + hasTodo + ", declIntact=" + declIntact + "\n" + written);
        mgr.remove(entry.name());
    }

    // --- P31: generic comma parameter does not break the erasure check ---

    void checkP31_genericCommaParamErasureClash() throws Exception {
        Files.writeString(srcDir.resolve("Pair.java"), "class Pair {}\n");
        Path file = srcDir.resolve("G.java");
        Files.writeString(file, "class G { void foo(int a, Pair b) {} }\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        AddMethodTool tool = new AddMethodTool(mgr, editMgr);
        CallToolResult r = tool.handle(null, req("add_method", Map.of(
                "name", entry.name(), "className", "G",
                "methodName", "foo", "returnType", "void",
                "parameters", "int x, Pair<K,V> m")));

        boolean isClash = !r.isError() && r.content().toString().contains("isDomainError")
                && r.content().toString().contains("erasure");
        report("P31 generic comma param erasure clash", isClash,
                "isError=" + r.isError() + ", content=" + r.content());
        mgr.remove(entry.name());
    }

    // --- P30: invalid targetType rejected via shared validateAnnotationTarget ---

    void checkP30_invalidTargetTypeRejected() throws Exception {
        Path file = srcDir.resolve("A.java");
        Files.writeString(file, "class A {}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        RemoveAnnotationTool tool = new RemoveAnnotationTool(mgr, editMgr);
        CallToolResult r = tool.handle(null, req("remove_annotation", Map.of(
                "name", entry.name(), "className", "A",
                "targetType", "bogus", "annotation", "Override")));

        boolean rejected = !r.isError() && r.content().toString().contains("isDomainError")
                && r.content().toString().contains("targetType must be");
        report("P30 invalid targetType rejected", rejected,
                "isError=" + r.isError() + ", content=" + r.content());
        mgr.remove(entry.name());
    }

    // --- P32: AddMethodTool error message says "signature or body" ---

    void checkP32_errorMessageSaysSignatureOrBody() throws Exception {
        Path file = srcDir.resolve("D.java");
        Files.writeString(file, "class D {}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        AddMethodTool tool = new AddMethodTool(mgr, editMgr);
        CallToolResult r = tool.handle(null, req("add_method", Map.of(
                "name", entry.name(), "className", "D",
                "methodName", "process", "returnType", "LocalDateTime")));

        boolean msgOk = !r.isError() && r.content().toString().contains("isDomainError")
                && r.content().toString().contains("signature or body");
        report("P32 error message says 'signature or body'", msgOk,
                "isError=" + r.isError() + ", content=" + r.content());
        mgr.remove(entry.name());
    }

    // --- P34: annotation inserted immediately before the declaration (dead loop removed) ---

    void checkP34_annotationInsertedBeforeDeclaration() throws Exception {
        Path file = srcDir.resolve("C.java");
        Files.writeString(file, "class C {\n    void work() {}\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        AddAnnotationTool tool = new AddAnnotationTool(mgr, editMgr);
        CallToolResult r = tool.handle(null, req("add_annotation", Map.of(
                "name", entry.name(), "className", "C",
                "targetType", "method", "targetName", "work",
                "annotation", "Deprecated")));

        String written = Files.readString(file);
        boolean annotated = written.lines().anyMatch(l -> l.trim().equals("@Deprecated"));
        boolean declSurvives = written.contains("void work()");
        report("P34 annotation before declaration", !r.isError() && annotated && declSurvives,
                "annotated=" + annotated + ", declSurvives=" + declSurvives + "\n" + written);
        mgr.remove(entry.name());
    }

    // --- helpers ---

    private void report(String label, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.println("[PASS] " + label);
        } else {
            fail++;
            System.out.println("[FAIL] " + label);
        }
        if (detail != null && !detail.isBlank()) {
            System.out.println("      " + detail.replace("\n", "\n      "));
        }
    }

    private static CallToolRequest req(String toolName, Map<String, Object> base) {
        Map<String, Object> args = new HashMap<>(base);
        return new CallToolRequest(toolName, args);
    }
}
