package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ListMethodsTool extends BaseJavaTool {

    public ListMethodsTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "list_methods"; }
    @Override protected String toolDescription() { return "Lists all methods in a class, or all methods across a project."; }
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription(
                "name", "string", "Project name or alias",
                "className", "string", "Optional: fully qualified class name");
    }
    @Override protected List<String> toolRequired() { return req("name"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        var entry = findEntry(name);

        StringBuilder sb = new StringBuilder("# Methods");

        if (className != null && !className.isBlank()) {
            var type = entry.model().getAllTypes().stream()
                    .filter(t -> t.getQualifiedName().equals(className))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Class not found: " + className));

            sb.append(" in `").append(className).append("`");
            sb.append(" (").append(entry.name()).append(")\n\n");

            for (var method : type.getMethods()) {
                String params = method.getParameters().stream()
                        .map(p -> p.getType().toString() + " " + p.getSimpleName())
                        .collect(Collectors.joining(", "));
                String retType = method.getType().toString();

                sb.append("- **").append(method.getSimpleName()).append("**(").append(params).append(")");
                sb.append(" → `").append(retType).append("`");
                if (method.isPublic()) sb.append(" `public`");
                else if (method.isProtected()) sb.append(" `protected`");
                else if (method.isPrivate()) sb.append(" `private`");
                if (method.isStatic()) sb.append(" `static`");
                sb.append("\n");
            }
        } else {
            sb.append(" (").append(entry.name()).append(")\n\n");
            sb.append("**All methods across project**\n\n");
            for (var type : entry.model().getAllTypes().stream()
                    .sorted(Comparator.comparing(t -> t.getQualifiedName())).toList()) {
                for (var method : type.getMethods()) {
                    String params = method.getParameters().stream()
                            .map(p -> p.getType().toString())
                            .collect(Collectors.joining(", "));
                    sb.append("- `").append(type.getQualifiedName()).append(".")
                      .append(method.getSimpleName()).append("(").append(params).append(")`\n");
                }
            }
        }

        return ok(sb);
    }
}
