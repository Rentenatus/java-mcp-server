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

import java.util.List;
import java.util.Map;

import spoon.reflect.code.CtComment;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * The {@code SearchSourceTool} class.
 *
 * @author Alejandro Ferreira
 */
public class SearchSourceTool extends BaseJavaTool {

/**
 * Constructs the {@code SearchSourceTool} with the given project manager.
 */
    public SearchSourceTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "search_source"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Searches for text within the source code of a loaded Java project across all elements."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "query", Map.of("type", "string", "description", "Text to search for"),
            "caseSensitive", Map.of("type", "boolean", "description", "Optional: case-sensitive search (default false)")
        );
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "query"); }

/**
 * Handles the {@code search_source} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String query = arg(request, "query");
        boolean caseSensitive = boolArg(request, "caseSensitive");
        var entry = findEntry(name);

        if (query == null || query.isBlank()) {
            return error("query cannot be empty");
        }

        String searchQuery = caseSensitive ? query : query.toLowerCase();

        StringBuilder sb = new StringBuilder();
        sb.append("# Search Results for `").append(query).append("` in ").append(entry.name()).append("\n\n");
        int total = 0;

        for (var type : entry.model().getAllTypes()) {
            for (var method : type.getMethods()) {
                if (method.getBody() != null) {
                    String bodyText = method.getBody().prettyprint();
                    String searchText = caseSensitive ? bodyText : bodyText.toLowerCase();
                    if (searchText.contains(searchQuery)) {
                        total++;
                        sb.append("- [").append(type.getQualifiedName()).append(".")
                          .append(method.getSimpleName()).append("(...)]\n");
                        String snippet = extractSnippet(bodyText, query, caseSensitive);
                        if (snippet != null) {
                            sb.append("  ```java\n  ").append(snippet).append("\n  ```\n");
                        }
                    }
                }
            }
        }

        if (total == 0) {
            sb.append("_No results found._");
        } else {
            sb.append("\n**Total matches:** ").append(total);
        }

        return ok(sb);
    }

/**
 * Extracts a context snippet around the first match of the query in the text.
 */
    private String extractSnippet(String text, String query, boolean caseSensitive) {
        String searchText = caseSensitive ? text : text.toLowerCase();
        String searchQuery = caseSensitive ? query : query.toLowerCase();
        int idx = searchText.indexOf(searchQuery);
        if (idx < 0) return null;
        int start = Math.max(0, idx - 40);
        int end = Math.min(text.length(), idx + query.length() + 40);
        String snippet = text.substring(start, end).replace('\n', ' ').replace('\r', ' ').trim();
        if (start > 0) snippet = "..." + snippet;
        if (end < text.length()) snippet = snippet + "...";
        return snippet;
    }
}
