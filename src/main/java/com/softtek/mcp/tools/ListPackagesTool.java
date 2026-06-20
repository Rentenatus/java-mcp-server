package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ListPackagesTool extends BaseJavaTool {

    public ListPackagesTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "list_packages"; }
    @Override protected String toolDescription() { return "Lists all packages in a loaded Java project."; }
    @Override protected Map<String, Object> toolProperties() { return propsWithDescription("name", "string", "Project name or alias"); }
    @Override protected List<String> toolRequired() { return req("name"); }

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
