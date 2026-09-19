package com.softtek_jare.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Reproduction tests for the 8 bugs documented in edit-tool-bugs.md.
 * Each test asserts the CORRECT (fixed) behaviour so that regressions are
 * caught immediately.
 */
class EditToolBugsReproTest {

    @TempDir
    Path tempDir;
    private ProjectManager mgr;
    private EditManager editMgr;
    private Path srcDir;

    @BeforeEach
    void setup() throws Exception {
        mgr = new ProjectManager();
        editMgr = new EditManager(tempDir.resolve("backups"));
        srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
    }

    // BUG 1: add_class must NOT return an error when the class is created.
    @Test
    void bug1_addClassReturnsSuccessNotError() throws Exception {
        Files.writeString(srcDir.resolve("Anchor.java"), "class Anchor {}");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        AddClassTool tool = new AddClassTool(mgr, editMgr);

        CallToolResult result = tool.handle(null, classReq(entry.name(), "demo", "WinLine", "class",
                "private int startRow;\nprivate int startCol;"));

        assertFalse(result.isError(), "add_class returned an error despite success: " + result.content().toString());
        assertTrue(Files.exists(srcDir.resolve("demo/WinLine.java")));
        mgr.remove(entry.name());
    }

    // BUG 2: add_class body content must be indented (4 spaces), not column 0.
    @Test
    void bug2_addClassBodyIsIndented() throws Exception {
        Files.writeString(srcDir.resolve("Anchor.java"), "class Anchor {}");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        AddClassTool tool = new AddClassTool(mgr, editMgr);

        tool.handle(null, classReq(entry.name(), "demo", "WinLine", "class",
                "private int startRow;\nprivate int startCol;"));

        String content = Files.readString(srcDir.resolve("demo/WinLine.java"));
        assertTrue(content.lines().anyMatch(l -> l.equals("    private int startRow;")),
                "body field should be indented 4 spaces, got:\n" + content);
        mgr.remove(entry.name());
    }

    // BUG 3a: add_method must insert the method exactly once (no duplication).
    @Test
    void bug3a_addMethodNoDuplication() throws Exception {
        Path file = srcDir.resolve("Box.java");
        Files.writeString(file, "class Box {\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        AddMethodTool tool = new AddMethodTool(mgr, editMgr);

        CallToolResult result = tool.handle(null, methodReq(
                entry.name(), "Box", "getStartRow", "int", null, "public", "return 0;"));
        assertFalse(result.isError(), result.content().toString());

        String content = Files.readString(file);
        long count = content.lines().filter(l -> l.contains("getStartRow")).count();
        assertEquals(1, count, "getStartRow should appear exactly once, got:\n" + content);
        mgr.remove(entry.name());
    }

    // BUG 3b: add_field must insert the field exactly once.
    @Test
    void bug3b_addFieldNoDuplication() throws Exception {
        Path file = srcDir.resolve("Cfg.java");
        Files.writeString(file, "class Cfg {\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        AddFieldTool tool = new AddFieldTool(mgr, editMgr);

        CallToolResult result = tool.handle(null, fieldReq(
                entry.name(), "Cfg", "diagonal", "boolean", "private", "false"));
        assertFalse(result.isError(), result.content().toString());

        String content = Files.readString(file);
        long count = content.lines().filter(l -> l.contains("diagonal")).count();
        assertEquals(1, count, "diagonal field should appear exactly once, got:\n" + content);
        mgr.remove(entry.name());
    }

    // BUG 3c: add_annotation must insert the annotation exactly once.
    @Test
    void bug3c_addAnnotationNoDuplication() throws Exception {
        Path file = srcDir.resolve("Svc.java");
        Files.writeString(file, "class Svc {\n  void handle() {}\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        AddAnnotationTool tool = new AddAnnotationTool(mgr, editMgr);

        CallToolResult result = tool.handle(null, annoReq(
                entry.name(), "class", "Svc", null, "Deprecated", null));
        assertFalse(result.isError(), result.content().toString());

        String content = Files.readString(file);
        long count = content.lines().filter(l -> l.contains("@Deprecated")).count();
        assertEquals(1, count, "@Deprecated should appear exactly once, got:\n" + content);
        mgr.remove(entry.name());
    }

    // BUG 4: add_method with methodName==className must emit a constructor
    // (no return type prefix), not "WinLine WinLine(...)".
    @Test
    void bug4_addMethodConstructorNoReturnType() throws Exception {
        Path file = srcDir.resolve("WinLine.java");
        Files.writeString(file, "class WinLine {\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        AddMethodTool tool = new AddMethodTool(mgr, editMgr);

        CallToolResult result = tool.handle(null, methodReq(
                entry.name(), "WinLine", "WinLine", "WinLine", "int startRow, int startCol", "public", null));
        assertFalse(result.isError(), result.content().toString());

        String content = Files.readString(file);
        // A constructor line should be "public WinLine(int startRow, int startCol)" — no
        // return-type prefix before the name.
        assertTrue(content.lines().anyMatch(l -> l.trim().matches("public\\s+WinLine\\s*\\(.*\\)\\s*\\{?")),
                "expected constructor 'public WinLine(...)', got:\n" + content);
        assertFalse(content.contains("WinLine WinLine("),
                "constructor must not have a return-type prefix, got:\n" + content);
        mgr.remove(entry.name());
    }

    // BUG 5: rewrite_signature changing only parameters must NOT report "unchanged".
    @Test
    void bug5_rewriteSignatureParamChangeReportsChanged() throws Exception {
        Path file = srcDir.resolve("Calc.java");
        Files.writeString(file, "class Calc {\n  int getStartRow() { return 0; }\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        RewriteSignatureTool tool = new RewriteSignatureTool(mgr, editMgr);

        CallToolResult result = tool.handle(null, rewriteReq(
                entry.name(), "Calc", "getStartRow", null, "int offset", "signature_only"));
        assertFalse(result.isError(), result.content().toString());

        String text = result.content().toString();
        assertFalse(text.contains("unchanged"),
                "changing parameters must not report 'unchanged', got: " + text);
        assertTrue(text.contains("changed"),
                "changing parameters must report 'changed', got: " + text);
        // The edit must be applied on disk.
        assertTrue(Files.readString(file).contains("getStartRow(int offset)"));
        mgr.remove(entry.name());
    }

    // BUG 6a: remove_member must return success (not error) when the member is removed.
    @Test
    void bug6a_removeMemberReturnsSuccess() throws Exception {
        Path file = srcDir.resolve("Svc.java");
        Files.writeString(file, "class Svc {\n  void handle() {}\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        RemoveMemberTool tool = new RemoveMemberTool(mgr, editMgr);

        CallToolResult result = tool.handle(null, removeMemberReq(
                entry.name(), "Svc", "handle", "method", "hard", null));
        assertFalse(result.isError(), "remove_member returned an error despite success: " + result.content().toString());
        assertFalse(Files.readString(file).contains("handle"));
        mgr.remove(entry.name());
    }

    // BUG 6b: remove_annotation must return success (not error) when removed.
    @Test
    void bug6b_removeAnnotationReturnsSuccess() throws Exception {
        Path file = srcDir.resolve("S.java");
        Files.writeString(file, "@Deprecated\nclass S {\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        RemoveAnnotationTool tool = new RemoveAnnotationTool(mgr, editMgr);

        CallToolResult result = tool.handle(null, removeAnnoReq(
                entry.name(), "class", "S", null, "Deprecated"));
        assertFalse(result.isError(), "remove_annotation returned an error despite success: " + result.content().toString());
        assertFalse(Files.readString(file).contains("@Deprecated"));
        mgr.remove(entry.name());
    }

    // BUG 7: edit_annotation must NOT remove sibling annotations.
    @Test
    void bug7_editAnnotationKeepsSiblingAnnotations() throws Exception {
        Path file = srcDir.resolve("W.java");
        Files.writeString(file,
                "@Deprecated\n@SuppressWarnings(value=\"unused\")\nclass W {\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        EditAnnotationTool tool = new EditAnnotationTool(mgr, editMgr);

        CallToolResult result = tool.handle(null, editAnnoReq(
                entry.name(), "class", "W", null, "SuppressWarnings", "value=\"rawtypes\""));
        assertFalse(result.isError(), result.content().toString());

        String content = Files.readString(file);
        assertTrue(content.contains("@Deprecated"),
                "@Deprecated must survive editing @SuppressWarnings, got:\n" + content);
        assertTrue(content.contains("value=\"rawtypes\""));
        assertFalse(content.contains("value=\"unused\""));
        mgr.remove(entry.name());
    }

    // BUG 8: add_annotation must reject @Override on a method that does not
    // override anything.
    @Test
    void bug8_addAnnotationOverrideValidated() throws Exception {
        Path file = srcDir.resolve("Solo.java");
        Files.writeString(file, "class Solo {\n  String getWinSymbol() { return \"\"; }\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        AddAnnotationTool tool = new AddAnnotationTool(mgr, editMgr);

        CallToolResult result = tool.handle(null, annoReq(
                entry.name(), "method", "Solo", "getWinSymbol", "Override", null));
        assertTrue(result.isError(),
                "@Override on a non-overriding method must be rejected, got: " + result.content().toString());
        mgr.remove(entry.name());
    }

    // --- request builders ---

    private static CallToolRequest classReq(String name, String pkg, String className,
            String type, String body) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("packageName", pkg);
        args.put("className", className);
        args.put("type", type);
        if (body != null) args.put("body", body);
        return new CallToolRequest("add_class", args);
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

    private static CallToolRequest annoReq(String name, String targetType, String className,
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

    private static CallToolRequest editAnnoReq(String name, String targetType, String className,
            String targetName, String annotation, String newAttributes) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("targetType", targetType);
        args.put("className", className);
        if (targetName != null) args.put("targetName", targetName);
        args.put("annotation", annotation);
        args.put("newAttributes", newAttributes);
        return new CallToolRequest("edit_annotation", args);
    }

    private static CallToolRequest removeAnnoReq(String name, String targetType, String className,
            String targetName, String annotation) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("targetType", targetType);
        args.put("className", className);
        if (targetName != null) args.put("targetName", targetName);
        args.put("annotation", annotation);
        return new CallToolRequest("remove_annotation", args);
    }

    private static CallToolRequest removeMemberReq(String name, String className,
            String memberName, String scope, String mode, String signature) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("className", className);
        args.put("memberName", memberName);
        args.put("scope", scope);
        args.put("mode", mode);
        if (signature != null) args.put("signature", signature);
        return new CallToolRequest("remove_member", args);
    }

    private static CallToolRequest rewriteReq(String name, String className, String methodName,
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
