package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.List;
import java.util.Map;

public class ResolveTypeTool extends BaseJavaTool {

    public ResolveTypeTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "resolve_type"; }
    @Override protected String toolDescription() { return "Resolves a simple class name to its fully qualified name(s) across the project."; }
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "simpleName", "string", "Simple class name (e.g. 'StringUtils')");
    }
    @Override protected List<String> toolRequired() { return req("name", "simpleName"); }

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
