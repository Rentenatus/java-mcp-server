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

import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The {@code ListDependenciesTool} class.
 *
 * @author Alejandro Ferreira
 */
public class ListDependenciesTool extends BaseJavaTool {

/**
 * Constructs the {@code ListDependenciesTool} with the given project manager.
 */
    public ListDependenciesTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "list_dependencies"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Lists external dependencies of a loaded Java project by scanning imports."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "group", "string", "Optional: filter by group/package prefix (e.g. 'org.springframework')");
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name"); }

/**
 * Handles the {@code list_dependencies} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String groupFilter = arg(request, "group");
        var entry = findEntry(name);

        Set<String> packages = new TreeSet<>();
        for (var type : entry.model().getAllTypes()) {
            type.getReferencedTypes().forEach(ref -> {
                String qn = ref.getQualifiedName();
                int dot = qn.lastIndexOf('.');
                if (dot > 0) {
                    String pkg = qn.substring(0, dot);
                    if (!pkg.startsWith("java.") && !pkg.startsWith("javax.")) {
                        packages.add(pkg);
                    }
                }
            });
        }

        StringBuilder sb = new StringBuilder("# Dependencies (").append(entry.name()).append(")\n\n");

        Set<String> displayPackages = packages;
        if (groupFilter != null && !groupFilter.isBlank()) {
            displayPackages = packages.stream()
                    .filter(p -> p.startsWith(groupFilter))
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        sb.append("**Total external packages referenced:** ").append(displayPackages.size()).append("\n\n");

        for (String pkg : displayPackages) {
            sb.append("- `").append(pkg).append("`\n");
        }

        return ok(sb);
    }
}
