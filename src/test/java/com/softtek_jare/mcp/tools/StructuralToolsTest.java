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
 * Tests for {@link RewriteSignatureTool}, {@link AddPackageTool}, {@link AddClassTool}, {@link MoveClassTool}.
 *
 * @author Janusch Rentenatus
 */
class StructuralToolsTest {

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

    @Test
    void rewriteSignatureChangesReturnType() throws Exception {
        Path file = srcDir.resolve("S.java");
        Files.writeString(file, "class S {\n  int getValue() {\n    return 0;\n  }\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        RewriteSignatureTool tool = new RewriteSignatureTool(mgr, editMgr);

        CallToolResult result = tool.handle(null, req(entry.name(), "S", "getValue", "long", null, null));

        assertFalse(result.isError());
        assertTrue(Files.readString(file).contains("long getValue"));
        mgr.remove(entry.name());
    }

    @Test
    void rewriteSignatureChangesParams() throws Exception {
        Path file = srcDir.resolve("C.java");
        Files.writeString(file, "class C {\n  int compute(int x) {\n    return x;\n  }\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        RewriteSignatureTool tool = new RewriteSignatureTool(mgr, editMgr);

        CallToolResult result = tool.handle(null, req(entry.name(), "C", "compute", "int", "int x, int y", null));

        assertFalse(result.isError());
        assertTrue(Files.readString(file).contains("int x, int y"));
        mgr.remove(entry.name());
    }

    @Test
    void rewriteSignatureAndCallersMarksCallSitesNotDeclaration() throws Exception {
        Path decl = srcDir.resolve("Svc.java");
        Files.writeString(decl, "class Svc {\n  int compute(int x) {\n    return x;\n  }\n}\n");
        Path caller = srcDir.resolve("Use.java");
        Files.writeString(caller, "class Use {\n  int run(Svc s) {\n    return s.compute(5);\n  }\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        RewriteSignatureTool tool = new RewriteSignatureTool(mgr, editMgr);

        CallToolResult result = tool.handle(null, req(entry.name(), "Svc", "compute",
                "int", "int x, int y", "signature_and_callers"));

        assertFalse(result.isError());
        // The declaring file must NOT get a TODO comment before its declaration.
        assertFalse(Files.readString(decl).contains("TODO"));
        // The caller file must get the TODO marker at the call site.
        assertTrue(Files.readString(caller).contains("TODO: signature of compute"));
        mgr.remove(entry.name());
    }

    @Test
    void addPackageCreatesDirectory() throws Exception {
        Files.writeString(srcDir.resolve("X.java"), "class X {}");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        AddPackageTool tool = new AddPackageTool(mgr, new com.softtek_jare.mcp.edit.EditManager());

        CallToolResult result = tool.handle(null, reqPkg(entry.name(), "com.example.service", false));

        assertFalse(result.isError());
        assertTrue(Files.isDirectory(srcDir.resolve("com/example/service")));
        mgr.remove(entry.name());
    }

    @Test
    void addPackageWithPackageInfo() throws Exception {
        Files.writeString(srcDir.resolve("X.java"), "class X {}");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        AddPackageTool tool = new AddPackageTool(mgr, new com.softtek_jare.mcp.edit.EditManager());

        CallToolResult result = tool.handle(null, reqPkg(entry.name(), "com.test", true));

        assertFalse(result.isError());
        assertTrue(Files.exists(srcDir.resolve("com/test/package-info.java")));
        mgr.remove(entry.name());
    }

    @Test
    void addClassCreatesFile() throws Exception {
        Files.writeString(srcDir.resolve("X.java"), "class X {}");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        AddClassTool tool = new AddClassTool(mgr, new com.softtek_jare.mcp.edit.EditManager());

        CallToolResult result = tool.handle(null, reqClass(entry.name(), "com.example", "MyService", "interface", null));

        assertFalse(result.isError());
        Path newFile = srcDir.resolve("com/example/MyService.java");
        assertTrue(Files.exists(newFile));
        String content = Files.readString(newFile);
        assertTrue(content.contains("package com.example;"));
        assertTrue(content.contains("interface MyService"));
        mgr.remove(entry.name());
    }

    @Test
    void addClassDuplicateDetected() throws Exception {
        Path existing = srcDir.resolve("Existing.java");
        Files.writeString(existing, "class Existing {}");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        AddClassTool tool = new AddClassTool(mgr, new com.softtek_jare.mcp.edit.EditManager());

        CallToolResult result = tool.handle(null, reqClass(entry.name(), "", "Existing", "class", null));

        assertTrue(result.isError());
        mgr.remove(entry.name());
    }

    @Test
    void addClassWithBodyResolvesImports() throws Exception {
        // A project type that the new class body should reference and auto-import.
        Path utilDir = srcDir.resolve("util");
        Files.createDirectories(utilDir);
        Files.writeString(utilDir.resolve("Helper.java"), "package util;\n\nclass Helper {}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        AddClassTool tool = new AddClassTool(mgr, new com.softtek_jare.mcp.edit.EditManager());

        CallToolResult result = tool.handle(null, reqClass(entry.name(), "demo",
                "Widget", "class", "  Helper helper;"));

        assertFalse(result.isError());
        Path newFile = srcDir.resolve("demo/Widget.java");
        assertTrue(Files.exists(newFile));
        String content = Files.readString(newFile);
        assertTrue(content.contains("import util.Helper;"));
        assertTrue(content.contains("Helper helper;"));
        mgr.remove(entry.name());
    }

    @Test
    void moveClassRelocatesAndRewrites() throws Exception {
        Path oldDir = srcDir.resolve("com/oldpkg");
        Files.createDirectories(oldDir);
        Files.writeString(oldDir.resolve("Old.java"), "package com.oldpkg;\n\nclass Old {}\n");
        Path userDir = srcDir.resolve("com/userpkg");
        Files.createDirectories(userDir);
        Path other = userDir.resolve("User.java");
        Files.writeString(other, "package com.userpkg;\n\nimport com.oldpkg.Old;\n\nclass User {\n  Old old = new Old();\n}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);
        MoveClassTool tool = new MoveClassTool(mgr, editMgr);

        CallToolResult result = tool.handle(null, reqMove(entry.name(), "com.oldpkg.Old", "com.newpkg"));

        assertFalse(result.isError());
        assertTrue(Files.exists(srcDir.resolve("com/newpkg/Old.java")));
        assertFalse(Files.exists(srcDir.resolve("com/oldpkg/Old.java")));
        assertTrue(Files.readString(other).contains("com.newpkg.Old"));
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

    private static CallToolRequest reqPkg(String name, String pkg, boolean genInfo) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("packageName", pkg);
        args.put("generatePackageInfo", genInfo);
        return new CallToolRequest("add_package", args);
    }

    private static CallToolRequest reqClass(String name, String pkg, String className,
            String type, String body) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("packageName", pkg);
        args.put("className", className);
        args.put("type", type);
        if (body != null) args.put("body", body);
        return new CallToolRequest("add_class", args);
    }

    private static CallToolRequest reqMove(String name, String className, String newPackage) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("className", className);
        args.put("newPackage", newPackage);
        return new CallToolRequest("move_class", args);
    }
}
