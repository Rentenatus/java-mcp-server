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
 * P52: Tests that multiple edits in sequence (without reload_java_project)
 * produce correct results. The in-memory model becomes stale after edits,
 * so tools must use fresh-parse positions rather than stale AST positions.
 */
class DirtyModelEditTest {

    @TempDir
    Path tempDir;
    private ProjectManager mgr;
    private EditManager editMgr;
    private AddFieldTool addField;
    private AddMethodTool addMethod;
    private AddAnnotationTool addAnno;
    private Path srcDir;

    @BeforeEach
    void setup() throws Exception {
        mgr = new ProjectManager();
        editMgr = new EditManager(tempDir.resolve("backups"));
        addField = new AddFieldTool(mgr, editMgr);
        addMethod = new AddMethodTool(mgr, editMgr);
        addAnno = new AddAnnotationTool(mgr, editMgr);
        srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
    }

    /**
     * Add 4 fields, then add a method — without reload. The method must be
     * inserted at class level, not inside an existing method (P42/P43).
     */
    @Test
    void sequentialAddFieldsThenAddMethod() throws Exception {
        Path file = srcDir.resolve("Widget.java");
        Files.writeString(file,
                "class Widget {\n" +
                "    void existing() {\n" +
                "        if (true) {\n" +
                "            System.out.println(\"hi\");\n" +
                "        }\n" +
                "    }\n" +
                "}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        // Add 4 fields in sequence (no reload between)
        for (int i = 0; i < 4; i++) {
            CallToolResult r = addField.handle(null, fieldReq(entry.name(), "Widget", "field" + i, "int", "private", "0"));
            assertFalse(r.isError(), "add_field field" + i + " failed: " + r.content());
        }

        // Now add a method — must go at class level, not inside existing()
        CallToolResult r = addMethod.handle(null, methodReq(entry.name(), "Widget", "getValue", "int", null, "public", "return 42;"));
        assertFalse(r.isError(), "add_method failed: " + r.content());

        String written = Files.readString(file);
        // The method must be a class-level member, not nested inside existing()
        // Verify "public int getValue()" appears at 4-space indent (class member)
        assertTrue(written.lines().anyMatch(l -> l.trim().startsWith("public int getValue") && l.startsWith("    ")),
                "getValue should be a class-level method, got:\n" + written);
        // Verify the body is at 8-space indent (inside method, not inside existing)
        assertTrue(written.lines().anyMatch(l -> l.equals("        return 42;")),
                "body should be indented 8 spaces, got:\n" + written);
        // Verify existing() method is still intact
        assertTrue(written.contains("void existing()"), "existing() method corrupted:\n" + written);
        mgr.remove(entry.name());
    }

    /**
     * Add a field, then add an annotation to an existing method — without reload.
     * The annotation must be inserted before the correct method (P44).
     */
    @Test
    void addFieldThenAnnotateMethod() throws Exception {
        Path file = srcDir.resolve("Service.java");
        Files.writeString(file,
                "class Service {\n" +
                "    void first() {\n" +
                "        int x = 1;\n" +
                "    }\n" +
                "    void second() {\n" +
                "        int y = 2;\n" +
                "    }\n" +
                "}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        // Add a field first (shifts line numbers)
        CallToolResult r1 = addField.handle(null, fieldReq(entry.name(), "Service", "counter", "int", "private", "0"));
        assertFalse(r1.isError());

        // Now annotate "second" — must go before second(), not before first()
        CallToolResult r2 = addAnno.handle(null, annoReq(entry.name(), "method", "Service", "second", "Override"));
        assertFalse(r2.isError(), "add_annotation failed: " + r2.content());

        String written = Files.readString(file);
        // @Override must be directly before void second()
        String[] lines = written.split("\n", -1);
        boolean foundAnnoBeforeSecond = false;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals("@Override") && i + 1 < lines.length
                    && lines[i + 1].contains("void second()")) {
                foundAnnoBeforeSecond = true;
                break;
            }
        }
        assertTrue(foundAnnoBeforeSecond, "@Override should be before second(), not before first():\n" + written);
        mgr.remove(entry.name());
    }

    /**
     * Add 2 fields, then add a method with a body, then add another field —
     * all without reload. Verify everything lands at class level (P42/P43 combined).
     */
    @Test
    void mixedFieldMethodFieldSequence() throws Exception {
        Path file = srcDir.resolve("Container.java");
        Files.writeString(file,
                "class Container {\n" +
                "    void init() {\n" +
                "        int a = 0;\n" +
                "        if (a > 0) {\n" +
                "            a++;\n" +
                "        }\n" +
                "    }\n" +
                "}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        // add_field
        assertFalse(addField.handle(null, fieldReq(entry.name(), "Container", "x", "int", "private", "0")).isError());
        // add_method
        assertFalse(addMethod.handle(null, methodReq(entry.name(), "Container", "run", "void", null, "public", "x++;")).isError());
        // add_field again
        assertFalse(addField.handle(null, fieldReq(entry.name(), "Container", "y", "int", "private", "0")).isError());

        String written = Files.readString(file);
        // All three must be class-level members
        assertTrue(written.contains("private int x = 0;"), "field x missing:\n" + written);
        assertTrue(written.contains("public void run()"), "method run missing:\n" + written);
        assertTrue(written.contains("private int y = 0;"), "field y missing:\n" + written);
        // init() must still be intact
        assertTrue(written.contains("void init()"), "init() corrupted:\n" + written);
        // run() body must contain x++ at method-body indent (8 spaces)
        assertTrue(written.lines().anyMatch(l -> l.equals("        x++;")),
                "run body should be 8-space indent:\n" + written);
        mgr.remove(entry.name());
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

    private static CallToolRequest annoReq(String name, String targetType, String className,
            String targetName, String annotation) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("targetType", targetType);
        args.put("className", className);
        args.put("targetName", targetName);
        args.put("annotation", annotation);
        return new CallToolRequest("add_annotation", args);
    }
}
