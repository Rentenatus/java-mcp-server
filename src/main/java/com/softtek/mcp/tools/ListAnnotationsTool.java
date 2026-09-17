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

import java.util.*;
import java.util.stream.Collectors;

/**
 * The {@code ListAnnotationsTool} class.
 *
 * @author Alejandro Ferreira
 */
public class ListAnnotationsTool extends BaseJavaTool {

/**
 * Constructs the {@code ListAnnotationsTool} with the given project manager.
 */
    public ListAnnotationsTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "list_annotations"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Lists all annotations used in a loaded Java project, or on a specific type."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "className", "string", "Optional: fully qualified class name to filter");
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name"); }

/**
 * Handles the {@code list_annotations} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        var entry = findEntry(name);

        if (className != null && !className.isBlank()) {
            var type = entry.model().getAllTypes().stream()
                    .filter(t -> t.getQualifiedName().equals(className))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Class not found: " + className));

            StringBuilder sb = new StringBuilder("# Annotations on `").append(className).append("`\n\n");
            Map<String, Integer> annCounts = new TreeMap<>();
            for (var ann : type.getAnnotations()) {
                String qn = ann.getAnnotationType().getQualifiedName();
                annCounts.merge(qn, 1, Integer::sum);
            }
            for (var method : type.getMethods()) {
                for (var ann : method.getAnnotations()) {
                    String qn = ann.getAnnotationType().getQualifiedName();
                    annCounts.merge(qn, 1, Integer::sum);
                }
            }
            for (var field : type.getFields()) {
                for (var ann : field.getAnnotations()) {
                    String qn = ann.getAnnotationType().getQualifiedName();
                    annCounts.merge(qn, 1, Integer::sum);
                }
            }

            for (var e : annCounts.entrySet()) {
                sb.append("- `@").append(e.getKey()).append("` (").append(e.getValue()).append(" occurrences)\n");
            }
            return ok(sb);
        }

        Map<String, Integer> globalCounts = new TreeMap<>();
        for (var type : entry.model().getAllTypes()) {
            for (var ann : type.getAnnotations()) {
                globalCounts.merge(ann.getAnnotationType().getQualifiedName(), 1, Integer::sum);
            }
        }

        StringBuilder sb = new StringBuilder("# Annotations (").append(entry.name()).append(")\n\n");
        sb.append("**Total distinct annotations:** ").append(globalCounts.size()).append("\n\n");
        for (var e : globalCounts.entrySet()) {
            sb.append("- `@").append(e.getKey()).append("` (").append(e.getValue()).append(" types)\n");
        }

        return ok(sb);
    }
}
