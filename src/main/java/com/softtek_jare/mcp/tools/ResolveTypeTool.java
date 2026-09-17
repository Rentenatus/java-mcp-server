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

import java.util.List;
import java.util.Map;

/**
 * The {@code ResolveTypeTool} class.
 *
 * @author Alejandro Ferreira
 */
public class ResolveTypeTool extends BaseJavaTool {

/**
 * Constructs the {@code ResolveTypeTool} with the given project manager.
 */
    public ResolveTypeTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "resolve_type"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Resolves a simple class name to its fully qualified name(s) across the project."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "simpleName", "string", "Simple class name (e.g. 'StringUtils')");
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "simpleName"); }

/**
 * Handles the {@code resolve_type} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String simpleName = arg(request, "simpleName");
        var entry = findEntry(name);

        var matches = entry.model().getAllTypes().stream()
                .filter(t -> t.getSimpleName().equals(simpleName))
                .map(t -> t.getQualifiedName())
                .sorted()
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("# Resolve: `").append(simpleName).append("`\n\n");

        if (matches.isEmpty()) {
            sb.append("_No types found with simple name '").append(simpleName).append("'_");
        } else {
            sb.append("**").append(matches.size()).append(" match(es):**\n\n");
            for (String fqn : matches) {
                sb.append("- `").append(fqn).append("`\n");
            }
        }

        return ok(sb);
    }
}
