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

package com.softtek_jare.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek_jare.mcp.ProjectManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The {@code FindAnnotatedElementsTool} class.
 *
 * @author Alejandro Ferreira
 */
public class FindAnnotatedElementsTool extends BaseJavaTool {

/**
 * Constructs the {@code FindAnnotatedElementsTool} with the given project manager.
 */
    public FindAnnotatedElementsTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "find_annotated_elements"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Finds all types, methods, or fields annotated with a given annotation."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "annotation", "string", "Fully qualified annotation name or simple name");
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "annotation"); }

/**
 * Handles the {@code find_annotated_elements} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String annotation = arg(request, "annotation");
        var entry = findEntry(name);

        StringBuilder sb = new StringBuilder("# Elements annotated with `@").append(annotation).append("`\n\n");

        for (var type : entry.model().getAllTypes()) {
            boolean typeMatch = type.getAnnotations().stream()
                    .anyMatch(a -> matches(a.getAnnotationType().getQualifiedName(), annotation));

            if (typeMatch) {
                sb.append("## Type: `").append(type.getQualifiedName()).append("`\n");
            }

            for (var method : type.getMethods()) {
                boolean methodMatch = method.getAnnotations().stream()
                        .anyMatch(a -> matches(a.getAnnotationType().getQualifiedName(), annotation));
                if (methodMatch) {
                    sb.append("- Method `").append(type.getQualifiedName()).append(".").append(method.getSimpleName()).append("(...)`\n");
                }
            }

            for (var field : type.getFields()) {
                boolean fieldMatch = field.getAnnotations().stream()
                        .anyMatch(a -> matches(a.getAnnotationType().getQualifiedName(), annotation));
                if (fieldMatch) {
                    sb.append("- Field `").append(type.getQualifiedName()).append(".").append(field.getSimpleName()).append("`\n");
                }
            }
        }

        if (sb.toString().equals("# Elements annotated with `@" + annotation + "`\n\n")) {
            sb.append("_No elements found._");
        }

        return ok(sb);
    }

/**
 * Checks whether an annotation name matches the search term by full or simple name.
 */
    private boolean matches(String qualifiedName, String search) {
        if (qualifiedName.equals(search)) return true;
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 && qualifiedName.substring(dot + 1).equals(search);
    }
}
