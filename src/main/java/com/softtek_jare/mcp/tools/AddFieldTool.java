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

import spoon.reflect.declaration.CtType;

/**
 * The {@code AddFieldTool} — adds a new field to an existing class.
 *
 * @author Janusch Rentenatus
 */
public class AddFieldTool extends BaseJavaTool {

    private final EditManager editManager;

    public AddFieldTool(ProjectManager manager, EditManager editManager) {
        super(manager);
        this.editManager = editManager;
    }

    @Override protected String toolName() { return "add_field"; }
    @Override protected String toolDescription() {
        return "Add a new field to an existing class. Automatic import resolution applies "
                + "to the field type and initializer expression.";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "fieldName", Map.of("type", "string", "description", "Field name"),
            "type", Map.of("type", "string", "description", "Field type (e.g. 'String', 'int')"),
            "modifiers", Map.of("type", "string", "description", "Optional: 'private', 'public', 'static', 'final'"),
            "initializer", Map.of("type", "string", "description", "Optional: initializer expression (e.g. '\"hello\"' or '42')")
        );
    }
    @Override protected java.util.List<String> toolRequired() { return req("name", "className", "fieldName", "type"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String fieldName = arg(request, "fieldName");
        String type = arg(request, "type");
        String modifiers = arg(request, "modifiers");
        String initializer = arg(request, "initializer");

        ProjectEntry entry = findEntry(name);
        requireEditable(entry);

        CtType<?> targetType = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        // Check for existing field with same name
        for (var field : targetType.getFields()) {
            if (field.getSimpleName().equals(fieldName)) {
                return error("Field '" + fieldName + "' already exists in class " + targetType.getSimpleName());
            }
        }

        // Auto-import resolution for type + initializer
        String checkText = type;
        if (initializer != null && !initializer.isBlank()) {
            checkText += " " + initializer;
        }
        var importResult = new AutoImportResolver().resolve(entry, targetType, checkText);
        if (!importResult.unresolvedTypes().isEmpty()) {
            return error("Cannot resolve type(s) in field declaration: " + importResult.unresolvedTypes()
                    + ". Provide the fully qualified name or add the dependency.");
        }

        // Build field source
        StringBuilder fieldSrc = new StringBuilder();
        if (modifiers != null && !modifiers.isBlank()) {
            fieldSrc.append(modifiers).append(" ");
        }
        fieldSrc.append(type).append(" ").append(fieldName);
        if (initializer != null && !initializer.isBlank()) {
            fieldSrc.append(" = ").append(initializer);
        }
        fieldSrc.append(";");

        // Insert before last closing brace
        Path file = targetType.getPosition().getFile() != null
                ? targetType.getPosition().getFile().toPath() : null;
        if (file == null) return error("Cannot determine source file.");

        String source = Files.readString(file);
        int lastBrace = source.lastIndexOf('}');
        if (lastBrace < 0) return error("Malformed source: no closing brace found.");

        String newContent = source.substring(0, lastBrace)
                + "    " + fieldSrc + "\n"
                + source.substring(lastBrace);
        newContent = insertImports(newContent, importResult.importsToAdd());

        entry = editManager.writeFile(entry, file, newContent, null);
        manager.updateEntry(entry);

        StringBuilder sb = new StringBuilder();
        sb.append("Field added: ").append(className).append(".").append(fieldName).append("\n");
        sb.append(formatMultiModuleWarning(entry));
        return ok(sb);
    }
}
