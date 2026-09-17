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

import spoon.reflect.declaration.CtField;
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
        String[] lines = source.split("\n", -1);
        int insertLine; // 0-indexed line before which to insert the annotation

        if ("class".equals(targetType)) {
            // Duplicate check
            boolean alreadyHas = type.getAnnotations().stream()
                    .anyMatch(a -> annotationMatches(a.getAnnotationType().getQualifiedName(), annotation));
            if (alreadyHas) return error("Class '" + className + "' already has annotation @" + annotation + ".");
            int declLine = type.getPosition().getLine();
            if (declLine < 1) return error("Cannot determine class declaration line.");
            insertLine = declLine - 1;
            // Walk backwards to skip modifiers (public, final, abstract, etc.)
            while (insertLine > 0 && lines[insertLine].trim().isEmpty()) insertLine--;
        } else if ("method".equals(targetType)) {
            if (targetName == null) return error("targetName required for method annotations.");
            CtMethod<?> method = type.getMethods().stream()
                    .filter(m -> m.getSimpleName().equals(targetName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Method '" + targetName + "' not found in " + className));
            // Duplicate check
            boolean alreadyHas = method.getAnnotations().stream()
                    .anyMatch(a -> annotationMatches(a.getAnnotationType().getQualifiedName(), annotation));
            if (alreadyHas) return error("Method '" + targetName + "' already has annotation @" + annotation + ".");
            int declLine = method.getPosition().getLine();
            if (declLine < 1) return error("Cannot determine method declaration line.");
            insertLine = declLine - 1;
            // Walk backwards to skip modifiers
            while (insertLine > 0 && lines[insertLine].trim().isEmpty()) insertLine--;
        } else if ("field".equals(targetType)) {
            if (targetName == null) return error("targetName required for field annotations.");
            CtField<?> field = type.getFields().stream()
                    .filter(f -> f.getSimpleName().equals(targetName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Field '" + targetName + "' not found in " + className));
            // Duplicate check
            boolean alreadyHas = field.getAnnotations().stream()
                    .anyMatch(a -> annotationMatches(a.getAnnotationType().getQualifiedName(), annotation));
            if (alreadyHas) return error("Field '" + targetName + "' already has annotation @" + annotation + ".");
            int declLine = field.getPosition().getLine();
            if (declLine < 1) return error("Cannot determine field declaration line.");
            insertLine = declLine - 1;
            // Walk backwards to skip modifiers
            while (insertLine > 0 && lines[insertLine].trim().isEmpty()) insertLine--;
        } else {
            return error("targetType must be 'class', 'method', or 'field'");
        }

        // Insert annotation line before the declaration
        String indent = "";
        for (char c : lines[insertLine].toCharArray()) {
            if (c == ' ') indent += " ";
            else break;
        }
        String[] newLines = new String[lines.length + 1];
        System.arraycopy(lines, 0, newLines, 0, insertLine);
        newLines[insertLine] = indent + annotationSrc;
        System.arraycopy(lines, insertLine, newLines, insertLine + 1, lines.length - insertLine);
        String newSource = String.join("\n", newLines);

        entry = editManager.writeFile(entry, file, newSource, null);
        manager.updateEntry(entry);
        editManager.logEdit(toolName());

        StringBuilder sb = new StringBuilder();
        sb.append("Annotation added: @").append(annotation).append(" on ").append(targetType);
        if (targetName != null) sb.append(" ").append(targetName);
        sb.append("\n");
        sb.append(formatMultiModuleWarning(entry));
        return ok(sb);
    }

    private static boolean annotationMatches(String qualifiedName, String search) {
        if (qualifiedName.equals(search)) return true;
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 && qualifiedName.substring(dot + 1).equals(search);
    }
}
