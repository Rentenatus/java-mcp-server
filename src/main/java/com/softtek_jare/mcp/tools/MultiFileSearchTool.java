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
 * The {@code MultiFileSearchTool} class.
 *
 * @author Alejandro Ferreira
 */
public class MultiFileSearchTool extends BaseJavaTool {

/**
 * Constructs the {@code MultiFileSearchTool} with the given project manager.
 */
    public MultiFileSearchTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "multi_file_search"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Searches for text across ALL currently loaded Java projects."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("query", "string", "Text to search for",
                "caseSensitive", "boolean", "Optional: case-sensitive search (default false)");
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("query"); }

/**
 * Handles the {@code multi_file_search} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String query = arg(request, "query");
        boolean caseSensitive = boolArg(request, "caseSensitive");

        if (query == null || query.isBlank()) {
            return domainError("DOMAIN_ERROR", "query cannot be empty");
        }

        var projects = manager.list();
        if (projects.isEmpty()) {
            return ok("No projects loaded. Call load_java_project first.");
        }

        String searchQuery = caseSensitive ? query : query.toLowerCase();
        StringBuilder sb = new StringBuilder();
        sb.append("# Multi-File Search: `").append(query).append("`\n\n");

        int total = 0;
        for (var entry : projects) {
            int fileCount = 0;
            StringBuilder fileSb = new StringBuilder();

            for (var type : entry.model().getAllTypes()) {
                for (var method : type.getMethods()) {
                    if (method.getBody() != null) {
                        String bodyText = method.getBody().prettyprint();
                        String searchText = caseSensitive ? bodyText : bodyText.toLowerCase();
                        if (searchText.contains(searchQuery)) {
                            fileCount++;
                            total++;
                            fileSb.append("  - `").append(type.getQualifiedName())
                                  .append(".").append(method.getSimpleName()).append("(...)`\n");
                        }
                    }
                }
            }

            if (fileCount > 0) {
                sb.append("## ").append(entry.name()).append(" (").append(fileCount).append(")\n");
                sb.append(fileSb);
            }
        }

        if (total == 0) {
            sb.append("_No results found._");
        } else {
            sb.append("\n**Total matches:** ").append(total).append(" across ").append(projects.size()).append(" project(s)");
        }

        return ok(sb);
    }
}
