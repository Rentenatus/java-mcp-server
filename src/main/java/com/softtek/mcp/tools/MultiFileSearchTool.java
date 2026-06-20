package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.List;
import java.util.Map;

public class MultiFileSearchTool extends BaseJavaTool {

    public MultiFileSearchTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "multi_file_search"; }
    @Override protected String toolDescription() { return "Searches for text across ALL currently loaded Java projects."; }
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("query", "string", "Text to search for",
                "caseSensitive", "boolean", "Optional: case-sensitive search (default false)");
    }
    @Override protected List<String> toolRequired() { return req("query"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String query = arg(request, "query");
        boolean caseSensitive = boolArg(request, "caseSensitive");

        if (query == null || query.isBlank()) {
            return error("query cannot be empty");
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
