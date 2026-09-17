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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        String source = Files.readString(file);
        // Match @Annotation or @Annotation(...) on its own line(s)
        Pattern pattern = Pattern.compile(
            "@\\Q" + annotation + "\\E(\\([^)]*\\))?\\s*\\n?\\s*",
            Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) {
            return error("Annotation '@" + annotation + "' not found on the specified target. "
                    + "No silent no-op — annotation must be present to remove.");
        }
        String newSource = matcher.replaceFirst("");
        entry = editManager.writeFile(entry, file, newSource, null);
        manager.updateEntry(entry);

        return ok("Annotation removed: @" + annotation + " from " + targetType
                + (targetName != null ? " " + targetName : "") + "\n" + formatMultiModuleWarning(entry));
    }
}
