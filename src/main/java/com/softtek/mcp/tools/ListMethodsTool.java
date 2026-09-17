/*
 * MIT License
 *
 * Copyright (c) 2026 Alejandro Ferreira
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

package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The {@code ListMethodsTool} class.
 *
 * @author Alejandro Ferreira
 */
public class ListMethodsTool extends BaseJavaTool {

/**
 * Constructs the {@code ListMethodsTool} with the given project manager.
 */
    public ListMethodsTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "list_methods"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Lists all methods in a class, or all methods across a project."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription(
                "name", "string", "Project name or alias",
                "className", "string", "Optional: fully qualified class name",
                "withJavadoc", "boolean", "If true (default), includes a one-line Javadoc summary per method");
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name"); }

/**
 * Handles the {@code list_methods} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        Boolean wj = (Boolean) request.arguments().get("withJavadoc");
        boolean withJavadoc = (wj == null) || wj;
        var entry = findEntry(name);

        StringBuilder sb = new StringBuilder("# Methods");

        if (className != null && !className.isBlank()) {
            var type = entry.model().getAllTypes().stream()
                    .filter(t -> t.getQualifiedName().equals(className))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Class not found: " + className));

            sb.append(" in `").append(className).append("`");
            sb.append(" (").append(entry.name()).append(")\n\n");

            for (var method : type.getMethods()) {
                String params = method.getParameters().stream()
                        .map(p -> p.getType().toString() + " " + p.getSimpleName())
                        .collect(Collectors.joining(", "));
                String retType = method.getType().toString();

                sb.append("- **").append(method.getSimpleName()).append("**(").append(params).append(")");
                sb.append(" → `").append(retType).append("`");
                if (method.isPublic()) sb.append(" `public`");
                else if (method.isProtected()) sb.append(" `protected`");
                else if (method.isPrivate()) sb.append(" `private`");
                if (method.isStatic()) sb.append(" `static`");
                sb.append("\n");
                if (withJavadoc) {
                    String summary = extractSummary(method.getDocComment());
                    if (summary != null) sb.append("  > ").append(summary).append("\n");
                }
            }
        } else {
            sb.append(" (").append(entry.name()).append(")\n\n");
            sb.append("**All methods across project**\n\n");
            for (var type : entry.model().getAllTypes().stream()
                    .sorted(Comparator.comparing(t -> t.getQualifiedName())).toList()) {
                for (var method : type.getMethods()) {
                    String params = method.getParameters().stream()
                            .map(p -> p.getType().toString())
                            .collect(Collectors.joining(", "));
                    sb.append("- `").append(type.getQualifiedName()).append(".")
                      .append(method.getSimpleName()).append("(").append(params).append(")`\n");
                    if (withJavadoc) {
                        String summary = extractSummary(method.getDocComment());
                        if (summary != null) sb.append("  > ").append(summary).append("\n");
                    }
                }
            }
        }

        // Scoped dirty check
        if (className != null && !className.isBlank()) {
            var checkType = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst().orElse(null);
            if (checkType != null) {
                sb.insert(0, formatDirtyWarning(checkDirty(entry, List.of(checkType))));
            }
        } else {
            sb.insert(0, formatDirtyWarning(checkDirty(entry, entry.model().getAllTypes().stream().toList())));
        }
        return ok(sb);
    }

/**
 * Extracts the first non-empty, non-annotation line from a Javadoc comment as a summary.
 */
    private static String extractSummary(String docComment) {
        if (docComment == null || docComment.isBlank()) return null;
        for (String raw : docComment.split("\n")) {
            String t = raw.trim();
            if (t.startsWith("*")) t = t.substring(1).trim();
            if (t.isEmpty() || t.startsWith("@")) continue;
            return t;
        }
        return null;
    }

}
