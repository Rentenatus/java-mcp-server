package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.*;
import java.util.stream.Collectors;

public class GetTypeHierarchyTool extends BaseJavaTool {

    public GetTypeHierarchyTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "get_type_hierarchy"; }
    @Override protected String toolDescription() { return "Shows the superclass and interface hierarchy for a given type."; }
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "className", "string", "Fully qualified class name");
    }
    @Override protected List<String> toolRequired() { return req("name", "className"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        var entry = findEntry(name);

        var type = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + className));

        StringBuilder sb = new StringBuilder();
        sb.append("# Type Hierarchy: `").append(className).append("`\n\n");

        List<String> hierarchy = new ArrayList<>();
        hierarchy.add("**" + className + "**");

        var superClass = type.getSuperclass();
        while (superClass != null) {
            hierarchy.add("↑ " + superClass.getQualifiedName());
            var superType = findType(entry, superClass.getQualifiedName());
            superClass = superType != null ? superType.getSuperclass() : null;
        }

        sb.append("### Inheritance\n\n");
        for (String h : hierarchy) {
            sb.append("- ").append(h).append("\n");
        }

        var interfaces = type.getSuperInterfaces();
        if (!interfaces.isEmpty()) {
            sb.append("\n### Implemented Interfaces\n\n");
            for (var iface : interfaces) {
                sb.append("- `").append(iface.getQualifiedName()).append("`\n");
            }
        }

        var subTypes = entry.model().getAllTypes().stream()
                .filter(t -> {
                    var sc = t.getSuperclass();
                    return sc != null && sc.getQualifiedName().equals(className);
                })
                .sorted(Comparator.comparing(t -> t.getQualifiedName()))
                .toList();

        if (!subTypes.isEmpty()) {
            sb.append("\n### Direct Subtypes\n\n");
            for (var st : subTypes) {
                sb.append("- `").append(st.getQualifiedName()).append("`\n");
            }
        }

        return ok(sb);
    }

    private spoon.reflect.declaration.CtType<?> findType(com.softtek.mcp.model.ProjectEntry entry, String qualifiedName) {
        return entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(qualifiedName))
                .findFirst()
                .orElse(null);
    }
}
