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
 * The {@code EditAnnotationTool} — changes the attributes of an existing annotation.
 * Returns an error if the annotation is not present (points to add_annotation).
 *
 * @author Janusch Rentenatus
 */
public class EditAnnotationTool extends BaseJavaTool {

    private final EditManager editManager;

    public EditAnnotationTool(ProjectManager manager, EditManager editManager) {
        super(manager);
        this.editManager = editManager;
    }

    @Override protected String toolName() { return "edit_annotation"; }
    @Override protected String toolDescription() {
        return "Change the attributes of an existing annotation. Errors if not present "
                + "(use add_annotation instead).";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "targetType", Map.of("type", "string", "description", "'class', 'method', or 'field'"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "targetName", Map.of("type", "string", "description", "Method or field name (omit for class)"),
            "annotation", Map.of("type", "string", "description", "Annotation name to edit"),
            "newAttributes", Map.of("type", "string", "description", "New key=value pairs (e.g. 'value=/new')")
        );
    }
    @Override protected java.util.List<String> toolRequired() {
        return req("name", "targetType", "className", "annotation", "newAttributes");
    }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String annotation = arg(request, "annotation");
        String newAttributes = arg(request, "newAttributes");
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

        // Determine the declaration line of the target member; the search window
        // is resolved after reading the source so the annotation block above the
        // declaration can be included (annotations sit above the declaration line).
        int declLine = -1;
        if ("method".equals(targetType) && targetName != null) {
            CtMethod<?> method = type.getMethods().stream()
                    .filter(m -> m.getSimpleName().equals(targetName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Method '" + targetName + "' not found in " + className));
            declLine = method.getPosition().getLine();
        } else if ("field".equals(targetType) && targetName != null) {
            CtField<?> field = type.getFields().stream()
                    .filter(f -> f.getSimpleName().equals(targetName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Field '" + targetName + "' not found in " + className));
            declLine = field.getPosition().getLine();
        } else if ("class".equals(targetType)) {
            declLine = type.getPosition().getLine();
        }

        String source = Files.readString(file);
        String[] lines = source.split("\n", -1);
        int searchStartLine;
        int searchEndLine;
        if (declLine > 0) {
            searchStartLine = annotationSearchStart(lines, declLine);
            searchEndLine = declLine;
        } else {
            searchStartLine = 1;
            searchEndLine = Integer.MAX_VALUE;
        }
        String replacement = "@" + annotation;
        if (newAttributes != null && !newAttributes.isBlank()) {
            replacement += "(" + newAttributes + ")";
        }
        // Calculate character offsets for the search window
        int startOffset = 0;
        for (int i = 0; i < searchStartLine - 1 && i < lines.length; i++) {
            startOffset += lines[i].length() + 1;
        }
        int endOffset = startOffset;
        for (int i = searchStartLine - 1; i < searchEndLine && i < lines.length; i++) {
            endOffset += lines[i].length() + 1;
        }
        endOffset = Math.min(endOffset, source.length());
        String newSource = replaceAnnotationInWindow(source, startOffset, endOffset, annotation, replacement);
        if (newSource == null) {
            return error("Annotation '@" + annotation + "' not found on " + targetType
                    + (targetName != null ? " '" + targetName + "'" : "") + ". Use add_annotation to add it first.");
        }
        entry = editManager.writeFile(entry, file, newSource, null);
        manager.updateEntry(entry);
        editManager.logEdit(toolName());

        return ok("Annotation edited: @" + annotation + " -> " + replacement + "\n"
                + formatMultiModuleWarning(entry));
    }
}
