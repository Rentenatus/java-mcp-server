package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.*;
import java.util.stream.Collectors;

public class FindAnnotatedElementsTool extends BaseJavaTool {

    public FindAnnotatedElementsTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "find_annotated_elements"; }
    @Override protected String toolDescription() { return "Finds all types, methods, or fields annotated with a given annotation."; }
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "annotation", "string", "Fully qualified annotation name or simple name");
    }
    @Override protected List<String> toolRequired() { return req("name", "annotation"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String annotation = arg(request, "annotation");
        var entry = findEntry(name);

        StringBuilder sb = new StringBuilder("# Elements annotated with `@").append(annotation).append("`\n\n");

        for (var type : entry.model().getAllTypes()) {
            boolean typeMatch = type.getAnnotations().stream()
                    .anyMatch(a -> matches(a.getAnnotationType().getQualifiedName(), annotation));

            if (typeMatch) {
                sb.append("## Type: `").append(type.getQualifiedName()).append("`\n");
            }

            for (var method : type.getMethods()) {
                boolean methodMatch = method.getAnnotations().stream()
                        .anyMatch(a -> matches(a.getAnnotationType().getQualifiedName(), annotation));
                if (methodMatch) {
                    sb.append("- Method `").append(type.getQualifiedName()).append(".").append(method.getSimpleName()).append("(...)`\n");
                }
            }

            for (var field : type.getFields()) {
                boolean fieldMatch = field.getAnnotations().stream()
                        .anyMatch(a -> matches(a.getAnnotationType().getQualifiedName(), annotation));
                if (fieldMatch) {
                    sb.append("- Field `").append(type.getQualifiedName()).append(".").append(field.getSimpleName()).append("`\n");
                }
            }
        }

        if (sb.toString().equals("# Elements annotated with `@" + annotation + "`\n\n")) {
            sb.append("_No elements found._");
        }

        return ok(sb);
    }

    private boolean matches(String qualifiedName, String search) {
        if (qualifiedName.equals(search)) return true;
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 && qualifiedName.substring(dot + 1).equals(search);
    }
}
