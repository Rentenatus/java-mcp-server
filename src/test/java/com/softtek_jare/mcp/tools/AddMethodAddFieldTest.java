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
        String written = Files.readString(file);
        assertTrue(written.contains("getValue"));
        assertTrue(written.contains("return 42"));
        // The body and closing brace must be indented, not at column 0.
        assertTrue(written.lines().anyMatch(l -> l.equals("        return 42;")),
                "body line should be indented 8 spaces, got:\n" + written);
        assertTrue(written.lines().anyMatch(l -> l.trim().equals("}") && l.startsWith("    ")),
                "method closing brace should be indented 4 spaces, got:\n" + written);
        mgr.remove(entry.name());
    }

    @Test
    void addMethodErasureClashDetected() throws Exception {
        Path file = srcDir.resolve("C.java");
        Files.writeString(file, "class C { void foo(int x) {} }\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addMethod.handle(null, methodReq(
                entry.name(), "C", "foo", "void", "int y", null, null));

        assertFalse(result.isError());
        assertTrue(result.content().toString().contains("isDomainError"));
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

        assertFalse(result.isError());
        assertTrue(result.content().toString().contains("isDomainError"));
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

        assertFalse(result.isError());
        assertTrue(result.content().toString().contains("isDomainError"));
        mgr.remove(entry.name());
    }

    @Test
    void addFieldUnresolvedTypeBlocks() throws Exception {
        Path file = srcDir.resolve("F.java");
        Files.writeString(file, "class F {}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addField.handle(null, fieldReq(
                entry.name(), "F", "when", "LocalDateTime", null, null));

        assertFalse(result.isError());
        assertTrue(result.content().toString().contains("isDomainError"));
        mgr.remove(entry.name());
    }

    @Test
    void addMethodErasureClashWithGenericCommaParam() throws Exception {
        // Existing foo(int, Pair). Adding foo(int, Pair<K,V>) has the same erased
        // signature [int, Pair] and must be reported as a clash. A naive comma
        // split would split "Pair<K,V> m" into two fragments and miss the clash.
        Path pair = srcDir.resolve("Pair.java");
        Files.writeString(pair, "class Pair {}\n");
        Path file = srcDir.resolve("G.java");
        Files.writeString(file, "class G { void foo(int a, Pair b) {} }\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addMethod.handle(null, methodReq(
                entry.name(), "G", "foo", "void", "int x, Pair<K,V> m", null, null));

        assertFalse(result.isError());
        assertTrue(result.content().toString().contains("isDomainError"));
        assertTrue(result.content().toString().contains("erasure"),
                "expected erasure clash, got: " + result.content());
        mgr.remove(entry.name());
    }

    @Test
    void addMethodToFirstOfTwoTopLevelTypes() throws Exception {
        // A file may contain several top-level types (only one may be public).
        // Adding a method to the FIRST type must insert before that type's
        // closing brace, not the last '}' in the file (which belongs to the
        // second type). findClassClosingBrace must respect the target type's
        // end line; inserting into the wrong type silently corrupts the sibling.
        Path file = srcDir.resolve("Pair.java");
        Files.writeString(file, """
            class First {
            }
            class Second {
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addMethod.handle(null, methodReq(
                entry.name(), "First", "bar", "void", null, "public", "return;"));

        assertFalse(result.isError(), () -> result.content().toString());
        String written = Files.readString(file);
        // The new method must be inside First, before First's closing brace.
        int barIdx = written.indexOf("public void bar");
        int firstClose = written.indexOf("}");
        assertTrue(barIdx >= 0, "new method missing:\n" + written);
        assertTrue(barIdx < firstClose,
                "new method must be inside First (before its closing brace):\n" + written);
        // Second must not receive the stray method.
        int secondOpen = written.indexOf("}", firstClose + 1);
        assertTrue(secondOpen < 0 || written.substring(firstClose + 1).indexOf("public void bar") < 0,
                "Second must not contain the new method:\n" + written);
        mgr.remove(entry.name());
    }

    @Test
    void addFieldWithManualImportDisambiguatesList() throws Exception {
        // java.util.List and java.awt.List share the simple name "List". Without
        // a manual import, add_field blocks with UNRESOLVED_TYPES. With the
        // imports parameter set to ["java.util.List"], the type resolves and the
        // import is inserted.
        Path file = srcDir.resolve("Hist.java");
        Files.writeString(file, "class Hist {\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addField.handle(null, fieldReq(
                entry.name(), "Hist", "items", "List<String>", "private", null,
                java.util.List.of("java.util.List")));

        assertFalse(result.isError(), () -> result.content().toString());
        String written = Files.readString(file);
        assertTrue(written.contains("import java.util.List;"),
                "import must be inserted, got:\n" + written);
        assertTrue(written.contains("private List<String> items;"),
                "field must use simple name, got:\n" + written);
        assertTrue(!written.contains("java.util.List<String> items"),
                "FQN must not leak into the field declaration, got:\n" + written);
        mgr.remove(entry.name());
    }

    @Test
    void addFieldWithInvalidManualImportReportedAsUnresolved() throws Exception {
        // A manual import that is neither a project type nor a loadable JDK class
        // must be reported as unresolved — the agent cannot inject a bogus import.
        Path file = srcDir.resolve("Bog.java");
        Files.writeString(file, "class Bog {\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addField.handle(null, fieldReq(
                entry.name(), "Bog", "x", "List<String>", "private", null,
                java.util.List.of("com.does.not.exist.List")));

        assertFalse(result.isError());
        assertTrue(result.content().toString().contains("isDomainError"));
        assertTrue(result.content().toString().contains("List"),
                "should mention the unresolved type, got: " + result.content());
        // The field must not have been added.
        String written = Files.readString(file);
        assertTrue(!written.contains("items"),
                "no field should be added on unresolved import, got:\n" + written);
        mgr.remove(entry.name());
    }

    @Test
    void addMethodWithManualImportDisambiguatesReturnType() throws Exception {
        // add_method with a return type "List<String>" that is ambiguous between
        // java.util.List and java.awt.List. The imports parameter resolves it.
        Path file = srcDir.resolve("Svc.java");
        Files.writeString(file, "class Svc {\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = addMethod.handle(null, methodReq(
                entry.name(), "Svc", "getItems", "List<String>", null, "public",
                "return null;", java.util.List.of("java.util.List")));

        assertFalse(result.isError(), () -> result.content().toString());
        String written = Files.readString(file);
        assertTrue(written.contains("import java.util.List;"),
                "import must be inserted, got:\n" + written);
        assertTrue(written.contains("public List<String> getItems"),
                "method must use simple name, got:\n" + written);
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

    private static CallToolRequest methodReq(String name, String className, String methodName,
            String returnType, String parameters, String modifiers, String body,
            java.util.List<String> imports) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("className", className);
        args.put("methodName", methodName);
        args.put("returnType", returnType);
        if (parameters != null) args.put("parameters", parameters);
        if (modifiers != null) args.put("modifiers", modifiers);
        if (body != null) args.put("body", body);
        if (imports != null) args.put("imports", imports);
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

    private static CallToolRequest fieldReq(String name, String className, String fieldName,
            String type, String modifiers, String initializer, java.util.List<String> imports) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("className", className);
        args.put("fieldName", fieldName);
        args.put("type", type);
        if (modifiers != null) args.put("modifiers", modifiers);
        if (initializer != null) args.put("initializer", initializer);
        if (imports != null) args.put("imports", imports);
        return new CallToolRequest("add_field", args);
    }
}
