package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.List;
import java.util.Map;

public class ProjectMetadataTool extends BaseJavaTool {

    public ProjectMetadataTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "project_metadata"; }
    @Override protected String toolDescription() { return "Retrieves metadata about a loaded Java project."; }
    @Override protected Map<String, Object> toolProperties() { return props("name", "string"); }
    @Override protected List<String> toolRequired() { return req("name"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        var entry = findEntry(name);
        var model = entry.model();

        long classCount = model.getAllTypes().stream().filter(t -> t.isClass()).count();
        long interfaceCount = model.getAllTypes().stream().filter(t -> t.isInterface()).count();
        long enumCount = model.getAllTypes().stream().filter(t -> t.isEnum()).count();
        long methodCount = model.getAllTypes().stream().mapToLong(t -> t.getMethods().size()).sum();
        long fieldCount = model.getAllTypes().stream().mapToLong(t -> t.getFields().size()).sum();
        long packageCount = model.getAllPackages().size();

        StringBuilder sb = new StringBuilder();
        sb.append("# Project Metadata: ").append(entry.name()).append("\n\n");
        sb.append("| Field | Value |\n");
        sb.append("|-------|-------|\n");
        sb.append("| **Name** | ").append(entry.name()).append(" |\n");
        if (!entry.alias().equals(entry.name())) {
            sb.append("| **Alias** | ").append(entry.alias()).append(" |\n");
        }
        sb.append("| **Build** | ").append(entry.buildType()).append(" |\n");
        sb.append("| **Directory** | ").append(entry.projectDir()).append(" |\n");
        sb.append("| **Packages** | ").append(packageCount).append(" |\n");
        sb.append("| **Classes** | ").append(classCount).append(" |\n");
        sb.append("| **Interfaces** | ").append(interfaceCount).append(" |\n");
        sb.append("| **Enums** | ").append(enumCount).append(" |\n");
        sb.append("| **Methods** | ").append(methodCount).append(" |\n");
        sb.append("| **Fields** | ").append(fieldCount).append(" |\n");

        return ok(sb);
    }
}
