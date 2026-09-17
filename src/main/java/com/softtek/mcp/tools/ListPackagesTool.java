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

/**
 * The {@code ListPackagesTool} class.
 *
 * @author Alejandro Ferreira
 */
public class ListPackagesTool extends BaseJavaTool {

/**
 * Constructs the {@code ListPackagesTool} with the given project manager.
 */
    public ListPackagesTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "list_packages"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Lists all packages in a loaded Java project."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() { return propsWithDescription("name", "string", "Project name or alias"); }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name"); }

/**
 * Handles the {@code list_packages} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        var entry = findEntry(name);
        var packages = entry.model().getAllPackages().stream()
                .map(p -> p.getQualifiedName())
                .filter(p -> !p.isEmpty())
                .sorted()
                .toList();

        StringBuilder sb = new StringBuilder("# Packages (").append(entry.name()).append(")\n\n");
        sb.append("**Total:** ").append(packages.size()).append(" packages\n\n");
        for (String pkg : packages) {
            long typeCount = entry.model().getAllTypes().stream()
                    .filter(t -> t.getPackage() != null && pkg.equals(t.getPackage().getQualifiedName()))
                    .count();
            sb.append("- `").append(pkg).append("` (").append(typeCount).append(" types)\n");
        }
        return ok(sb);
    }
}
