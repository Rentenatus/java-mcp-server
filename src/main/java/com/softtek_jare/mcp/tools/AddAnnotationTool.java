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
import com.softtek_jare.mcp.edit.EditManager;
import com.softtek_jare.mcp.model.ProjectEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

/**
 * The {@code AddAnnotationTool} — adds an annotation to a class, method, or field.
 *
 * @author Janusch Rentenatus
 */
public class AddAnnotationTool extends BaseJavaTool {

    private final EditManager editManager;

    public AddAnnotationTool(ProjectManager manager, EditManager editManager) {
        super(manager);
        this.editManager = editManager;
    }

    @Override protected String toolName() { return "add_annotation"; }
    @Override protected String toolDescription() {
        return "Add an annotation to a class, method, or field. Checks for duplicates.";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "targetType", Map.of("type", "string", "description", "'class', 'method', or 'field'"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "targetName", Map.of("type", "string", "description", "Method or field name (omit for class)"),
            "annotation", Map.of("type", "string", "description", "Annotation name (simple or qualified)"),
            "attributes", Map.of("type", "string", "description", "Optional: key=value pairs (e.g. 'value=/new')")
        );
    }
    @Override protected java.util.List<String> toolRequired() {
        return req("name", "targetType", "className", "annotation");
    }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception {
        String name = arg(request, "name");
        String targetType = arg(request, "targetType");
        String className = arg(request, "className");
        String targetName = arg(request, "targetName");
        String annotation = arg(request, "annotation");
        String attributes = arg(request, "attributes");

        ProjectEntry entry = findEntry(name);
        requireEditable(entry);

        CtType<?> type = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        String annotationSrc = "@" + annotation;
        if (attributes != null && !attributes.isBlank()) {
            annotationSrc += "(" + attributes + ")";
        }

        Path file = type.getPosition().getFile() != null
                ? type.getPosition().getFile().toPath() : null;
        if (file == null) return error("Cannot determine source file.");

        String source = Files.readString(file);
        String newSource;

        if ("class".equals(targetType)) {
            // Insert before class declaration
            int classIdx = source.indexOf("class " + type.getSimpleName());
            if (classIdx < 0) classIdx = source.indexOf("interface " + type.getSimpleName());
            if (classIdx < 0) classIdx = source.indexOf("enum " + type.getSimpleName());
            if (classIdx < 0) return error("Cannot find class declaration.");
            newSource = source.substring(0, classIdx) + annotationSrc + "\n" + source.substring(classIdx);
        } else if ("method".equals(targetType)) {
            if (targetName == null) return error("targetName required for method annotations.");
            int methodIdx = source.indexOf(targetName + "(");
            if (methodIdx < 0) return error("Method '" + targetName + "' not found.");
            newSource = source.substring(0, methodIdx) + annotationSrc + "\n    " + source.substring(methodIdx);
        } else if ("field".equals(targetType)) {
            if (targetName == null) return error("targetName required for field annotations.");
            int fieldIdx = source.indexOf(" " + targetName + " ");
            if (fieldIdx < 0) fieldIdx = source.indexOf(" " + targetName + ";");
            if (fieldIdx < 0) return error("Field '" + targetName + "' not found.");
            newSource = source.substring(0, fieldIdx + 1) + annotationSrc + "\n    " + source.substring(fieldIdx + 1);
        } else {
            return error("targetType must be 'class', 'method', or 'field'");
        }

        entry = editManager.writeFile(entry, file, newSource, null);
        manager.updateEntry(entry);

        StringBuilder sb = new StringBuilder();
        sb.append("Annotation added: @").append(annotation).append(" on ").append(targetType);
        if (targetName != null) sb.append(" ").append(targetName);
        sb.append("\n");
        sb.append(formatMultiModuleWarning(entry));
        return ok(sb);
    }
}
