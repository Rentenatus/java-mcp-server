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
 * The {@code GetTypeHierarchyTool} class.
 *
 * @author Alejandro Ferreira
 */
public class GetTypeHierarchyTool extends BaseJavaTool {

/**
 * Constructs the {@code GetTypeHierarchyTool} with the given project manager.
 */
    public GetTypeHierarchyTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "get_type_hierarchy"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Shows the superclass and interface hierarchy for a given type."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "className", "string", "Fully qualified class name");
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "className"); }

/**
 * Handles the {@code get_type_hierarchy} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        var entry = findEntry(name);

        var type = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + className));

        StringBuilder sb = new StringBuilder();
        sb.append("# Type Hierarchy: `").append(className).append("`\n\n");

        List<String> hierarchy = new ArrayList<>();
        hierarchy.add("**" + className + "**");

        var superClass = type.getSuperclass();
        while (superClass != null) {
            hierarchy.add("↑ " + superClass.getQualifiedName());
            var superType = findType(entry, superClass.getQualifiedName());
            superClass = superType != null ? superType.getSuperclass() : null;
        }

        sb.append("### Inheritance\n\n");
        for (String h : hierarchy) {
            sb.append("- ").append(h).append("\n");
        }

        var interfaces = type.getSuperInterfaces();
        if (!interfaces.isEmpty()) {
            sb.append("\n### Implemented Interfaces\n\n");
            for (var iface : interfaces) {
                sb.append("- `").append(iface.getQualifiedName()).append("`\n");
            }
        }

        var subTypes = entry.model().getAllTypes().stream()
                .filter(t -> {
                    var sc = t.getSuperclass();
                    return sc != null && sc.getQualifiedName().equals(className);
                })
                .sorted(Comparator.comparing(t -> t.getQualifiedName()))
                .toList();

        if (!subTypes.isEmpty()) {
            sb.append("\n### Direct Subtypes\n\n");
            for (var st : subTypes) {
                sb.append("- `").append(st.getQualifiedName()).append("`\n");
            }
        }

        return ok(sb);
    }

/**
 * Looks up a type by qualified name in the project model.
 */
    private spoon.reflect.declaration.CtType<?> findType(com.softtek.mcp.model.ProjectEntry entry, String qualifiedName) {
        return entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(qualifiedName))
                .findFirst()
                .orElse(null);
    }
}
