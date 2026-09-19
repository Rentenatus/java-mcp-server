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

import spoon.reflect.declaration.CtType;

/**
 * The {@code RemoveAnnotationTool} — removes an annotation from a class, method, or field.
 * Returns an error if the annotation is not present (no silent no-op).
 *
 * @author Janusch Rentenatus
 */
public class RemoveAnnotationTool extends BaseJavaTool {

    private final EditManager editManager;

    public RemoveAnnotationTool(ProjectManager manager, EditManager editManager) {
        super(manager);
        this.editManager = editManager;
    }

    @Override protected String toolName() { return "remove_annotation"; }
    @Override protected String toolDescription() {
        return "Remove an annotation from a class, method, or field. Errors if not present.";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "targetType", Map.of("type", "string", "description", "'class', 'method', or 'field'"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "targetName", Map.of("type", "string", "description", "Method or field name (omit for class)"),
            "annotation", Map.of("type", "string", "description", "Annotation name to remove")
        );
    }
    @Override protected java.util.List<String> toolRequired() {
        return req("name", "targetType", "className", "annotation");
    }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String annotation = arg(request, "annotation");
        String targetType = arg(request, "targetType");
        String targetName = arg(request, "targetName");

        ProjectEntry entry = findEntry(name);
        requireEditable(entry);

        CtType<?> type = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        Path file = type.getPosition().getFile() != null
                ? type.getPosition().getFile().toPath() : null;
        if (file == null) return error("Cannot determine source file.");

        if (!"class".equals(targetType) && !"method".equals(targetType) && !"field".equals(targetType)) {
            return error("targetType must be 'class', 'method', or 'field'");
        }
        if (("method".equals(targetType) || "field".equals(targetType)) && (targetName == null || targetName.isBlank())) {
            return error("targetName required for method and field annotations");
        }

        String source = Files.readString(file);
        int[] window = annotationWindow(source, type, targetType, targetName);
        int startOffset = window[0];
        int endOffset = window[1];
        String newSource = replaceAnnotationInWindow(source, startOffset, endOffset, annotation, "");
        if (newSource == null) {
            return error("Annotation '@" + annotation + "' not found on " + targetType
                    + (targetName != null ? " '" + targetName + "'" : "") + ". "
                    + "No silent no-op — annotation must be present to remove.");
        }
        // Post-process: remove lines that became empty after annotation removal
        String[] before = source.split("\n", -1);
        String[] after = newSource.split("\n", -1);
        if (after.length == before.length) {
            StringBuilder cleaned = new StringBuilder();
            for (int i = 0; i < after.length; i++) {
                if (after[i].trim().isEmpty() && !before[i].trim().isEmpty()) {
                    continue; // skip line that became empty due to annotation removal
                }
                if (cleaned.length() > 0) cleaned.append("\n");
                cleaned.append(after[i]);
            }
            newSource = cleaned.toString();
        }
        entry = editManager.writeFile(entry, file, newSource, null);
        manager.updateEntry(entry);
        editManager.logEdit(toolName());

        return ok("Annotation removed: @" + annotation + " from " + targetType
                + (targetName != null ? " " + targetName : "") + "\n" + formatMultiModuleWarning(entry));
    }
}
