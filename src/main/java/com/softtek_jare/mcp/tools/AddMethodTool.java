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
import com.softtek_jare.mcp.edit.TypeErasureChecker;
import com.softtek_jare.mcp.model.ProjectEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import spoon.reflect.declaration.CtType;

/**
 * The {@code AddMethodTool} — adds a new method to an existing class.
 *
 * @author Janusch Rentenatus
 */
public class AddMethodTool extends BaseJavaTool {

    private final EditManager editManager;

    public AddMethodTool(ProjectManager manager, EditManager editManager) {
        super(manager);
        this.editManager = editManager;
    }

    @Override protected String toolName() { return "add_method"; }
    @Override protected String toolDescription() {
        return "Add a new method to an existing class. Checks for erasure collisions. "
                + "Automatic import resolution applies to types in the body.";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "methodName", Map.of("type", "string", "description", "Method name"),
            "returnType", Map.of("type", "string", "description", "Return type (e.g. 'void', 'String')"),
            "parameters", Map.of("type", "string", "description", "Comma-separated 'type name' pairs (e.g. 'int count, String label')"),
            "modifiers", Map.of("type", "string", "description", "Optional: 'public', 'private', 'protected', 'static'"),
            "body", Map.of("type", "string", "description", "Optional: method body (without braces)")
        );
    }
    @Override protected List<String> toolRequired() { return req("name", "className", "methodName", "returnType"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String methodName = arg(request, "methodName");
        String returnType = arg(request, "returnType");
        String parameters = arg(request, "parameters");
        String modifiers = arg(request, "modifiers");
        String body = arg(request, "body");

        ProjectEntry entry = findEntry(name);
        requireEditable(entry);

        CtType<?> targetType = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        // Parse parameter types for erasure check
        List<String> paramTypes = new ArrayList<>();
        if (parameters != null && !parameters.isBlank()) {
            for (String p : parameters.split(",")) {
                String trimmed = p.trim();
                // Skip 'final' modifier to get the actual type
                String[] parts = trimmed.split("\\s+");
                String paramType = parts[0];
                if ("final".equals(paramType) && parts.length > 1) {
                    paramType = parts[1];
                }
                paramTypes.add(paramType);
            }
        }

        // Erasure check
        var clash = new TypeErasureChecker().checkErasure(targetType, methodName, paramTypes);
        if (clash.clash()) {
            return error(clash.message());
        }

        // Auto-import resolution for body
        var importResult = new com.softtek_jare.mcp.edit.AutoImportResolver.Result(java.util.List.of(), java.util.List.of());
        if (body != null && !body.isBlank()) {
            importResult = new AutoImportResolver().resolve(entry, targetType, body);
            if (!importResult.unresolvedTypes().isEmpty()) {
                return error("Cannot resolve type(s) in method body: " + importResult.unresolvedTypes()
                        + ". Provide the fully qualified name or add the dependency.");
            }
        }

        // Build method source string
        StringBuilder methodSrc = new StringBuilder();
        if (modifiers != null && !modifiers.isBlank()) {
            methodSrc.append(modifiers).append(" ");
        }
        methodSrc.append(returnType).append(" ").append(methodName).append("(");
        if (parameters != null && !parameters.isBlank()) {
            methodSrc.append(parameters);
        }
        methodSrc.append(")");
        if (body != null && !body.isBlank()) {
            methodSrc.append(" {\n").append(body).append("\n}");
        } else {
            methodSrc.append(" {\n}");
        }

        // Insert before last closing brace of the class
        Path file = targetType.getPosition().getFile() != null
                ? targetType.getPosition().getFile().toPath() : null;
        if (file == null) return error("Cannot determine source file.");

        String source = Files.readString(file);
        int lastBrace = source.lastIndexOf('}');
        if (lastBrace < 0) return error("Malformed source: no closing brace found.");

        String newContent = source.substring(0, lastBrace)
                + "    " + methodSrc + "\n"
                + source.substring(lastBrace);
        if (body != null && !body.isBlank()) {
            newContent = insertImports(newContent, importResult.importsToAdd());
        }

        entry = editManager.writeFile(entry, file, newContent, null);
        manager.updateEntry(entry);
        editManager.logEdit(toolName());

        StringBuilder sb = new StringBuilder();
        sb.append("Method added: ").append(className).append(".").append(methodName).append("\n");
        sb.append(formatMultiModuleWarning(entry));
        return ok(sb);
    }
}
