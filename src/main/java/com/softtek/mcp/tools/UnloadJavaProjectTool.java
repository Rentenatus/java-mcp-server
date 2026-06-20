package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.List;
import java.util.Map;

public class UnloadJavaProjectTool extends BaseJavaTool {

    public UnloadJavaProjectTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "unload_java_project"; }
    @Override protected String toolDescription() { return "Removes a loaded Java project from memory."; }
    @Override protected Map<String, Object> toolProperties() { return props("name", "string"); }
    @Override protected List<String> toolRequired() { return req("name"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        var entry = manager.remove(name);
        if (entry == null) {
            return ok("Project '" + name + "' was not loaded.");
        }
        return ok("Project '" + entry.name() + "' unloaded successfully.");
    }
}
