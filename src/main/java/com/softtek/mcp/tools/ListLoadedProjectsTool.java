package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.List;
import java.util.Map;

public class ListLoadedProjectsTool extends BaseJavaTool {

    public ListLoadedProjectsTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "list_loaded_projects"; }
    @Override protected String toolDescription() { return "Lists all Java projects currently loaded in memory."; }
    @Override protected Map<String, Object> toolProperties() { return Map.of(); }
    @Override protected List<String> toolRequired() { return List.of(); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        var entries = manager.list();
        if (entries.isEmpty()) {
            return ok("# Loaded Projects\n\n_No projects loaded._");
        }

        StringBuilder sb = new StringBuilder("# Loaded Projects\n\n");
        for (var entry : entries) {
            String types = String.valueOf(entry.model().getAllTypes().size());
            sb.append("- **").append(entry.name()).append("**");
            if (!entry.alias().equals(entry.name())) {
                sb.append(" (alias: `").append(entry.alias()).append("`)");
            }
            sb.append(" — ").append(types).append(" types, build: ").append(entry.buildType());
            if (entry.expiryDate() != null) {
                sb.append(" [expires: ").append(entry.expiryDate()).append("]");
            }
            sb.append("\n");
        }
        return ok(sb);
    }
}
