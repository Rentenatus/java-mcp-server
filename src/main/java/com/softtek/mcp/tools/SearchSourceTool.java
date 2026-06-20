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

public class SearchSourceTool extends BaseJavaTool {

    public SearchSourceTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "search_source"; }
    @Override protected String toolDescription() { return "Searches for text within the source code of a loaded Java project across all elements."; }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "query", Map.of("type", "string", "description", "Text to search for"),
            "caseSensitive", Map.of("type", "boolean", "description", "Optional: case-sensitive search (default false)")
        );
    }
    @Override protected List<String> toolRequired() { return req("name", "query"); }

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
