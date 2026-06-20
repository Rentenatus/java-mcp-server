package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.*;
import java.util.stream.Collectors;

public class ListAnnotationsTool extends BaseJavaTool {

    public ListAnnotationsTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "list_annotations"; }
    @Override protected String toolDescription() { return "Lists all annotations used in a loaded Java project, or on a specific type."; }
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "className", "string", "Optional: fully qualified class name to filter");
    }
    @Override protected List<String> toolRequired() { return req("name"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        var entry = findEntry(name);

        if (className != null && !className.isBlank()) {
            var type = entry.model().getAllTypes().stream()
                    .filter(t -> t.getQualifiedName().equals(className))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Class not found: " + className));

            StringBuilder sb = new StringBuilder("# Annotations on `").append(className).append("`\n\n");
            Map<String, Integer> annCounts = new TreeMap<>();
            for (var ann : type.getAnnotations()) {
                String qn = ann.getAnnotationType().getQualifiedName();
                annCounts.merge(qn, 1, Integer::sum);
            }
            for (var method : type.getMethods()) {
                for (var ann : method.getAnnotations()) {
                    String qn = ann.getAnnotationType().getQualifiedName();
                    annCounts.merge(qn, 1, Integer::sum);
                }
            }
            for (var field : type.getFields()) {
                for (var ann : field.getAnnotations()) {
                    String qn = ann.getAnnotationType().getQualifiedName();
                    annCounts.merge(qn, 1, Integer::sum);
                }
            }

            for (var e : annCounts.entrySet()) {
                sb.append("- `@").append(e.getKey()).append("` (").append(e.getValue()).append(" occurrences)\n");
            }
            return ok(sb);
        }

        Map<String, Integer> globalCounts = new TreeMap<>();
        for (var type : entry.model().getAllTypes()) {
            for (var ann : type.getAnnotations()) {
                globalCounts.merge(ann.getAnnotationType().getQualifiedName(), 1, Integer::sum);
            }
        }

        StringBuilder sb = new StringBuilder("# Annotations (").append(entry.name()).append(")\n\n");
        sb.append("**Total distinct annotations:** ").append(globalCounts.size()).append("\n\n");
        for (var e : globalCounts.entrySet()) {
            sb.append("- `@").append(e.getKey()).append("` (").append(e.getValue()).append(" types)\n");
        }

        return ok(sb);
    }
}
