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

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek_jare.mcp.ProjectManager;
import com.softtek_jare.mcp.edit.AutoImportResolver;
import com.softtek_jare.mcp.edit.EditManager;
import com.softtek_jare.mcp.model.ProjectEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The {@code AddClassTool} — creates a new class, enum, interface, or abstract class.
 *
 * @author Janusch Rentenatus
 */
public class AddClassTool extends BaseJavaTool {

    private final EditManager editManager;

    public AddClassTool(ProjectManager manager, EditManager editManager) {
        super(manager);
        this.editManager = editManager;
    }

    @Override protected String toolName() { return "add_class"; }
    @Override protected String toolDescription() {
        return "Create a new class, enum, interface, or abstract class in a package. "
                + "Automatic import resolution applies to types in body.";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "packageName", Map.of("type", "string", "description", "Package name (e.g. 'com.example')"),
            "className", Map.of("type", "string", "description", "Class name"),
            "type", Map.of("type", "string", "description", "'class', 'enum', 'interface', or 'abstract' (default: class)"),
            "body", Map.of("type", "string", "description", "Optional: class body (fields, methods)")
        );
    }
    @Override protected java.util.List<String> toolRequired() { return req("name", "packageName", "className"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception {
        String name = arg(request, "name");
        String packageName = arg(request, "packageName");
        String className = arg(request, "className");
        String type = arg(request, "type");
        String body = arg(request, "body");
        if (type == null) type = "class";

        ProjectEntry entry = findEntry(name);
        requireEditable(entry);

        Path srcDir = entry.projectDir();
        if (srcDir == null) srcDir = entry.originalProjectDir();
        if (srcDir == null) return error("Cannot determine source directory.");

        Path packageDir = srcDir.resolve(packageName.replace(".", "/"));
        Path file = packageDir.resolve(className + ".java");
        if (Files.exists(file)) {
            return error("Class '" + className + "' already exists at " + file);
        }
        Files.createDirectories(packageDir);

        // Auto-import resolution for body (target class does not exist yet)
        var importResult = new com.softtek_jare.mcp.edit.AutoImportResolver.Result(java.util.List.of(), java.util.List.of());
        if (body != null && !body.isBlank()) {
            importResult = new AutoImportResolver().resolve(entry, null, body);
            if (!importResult.unresolvedTypes().isEmpty()) {
                return error("Cannot resolve type(s) in body: " + importResult.unresolvedTypes()
                        + ". Provide the fully qualified name or add the dependency.");
            }
        }

        StringBuilder src = new StringBuilder();
        src.append("package ").append(packageName).append(";\n\n");
        String declKeyword = "abstract".equals(type) ? "abstract class" : type;
        src.append(declKeyword).append(" ").append(className).append(" {\n");
        if (body != null && !body.isBlank()) {
            src.append(body).append("\n");
        }
        src.append("}\n");

        // Insert any imports resolved from the body before writing
        String content = insertImports(src.toString(), importResult.importsToAdd());

        entry = editManager.writeFile(entry, file, content, null);
        manager.updateEntry(entry);
        editManager.logEdit(toolName());

        return ok("Class created: " + packageName + "." + className + " at " + file
                + "\n" + formatMultiModuleWarning(entry));
    }
}
