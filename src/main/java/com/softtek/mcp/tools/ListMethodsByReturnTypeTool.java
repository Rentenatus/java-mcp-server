package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ListMethodsByReturnTypeTool extends BaseJavaTool {

    public ListMethodsByReturnTypeTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "list_methods_by_return_type"; }
    @Override protected String toolDescription() { return "Lists all methods in the project that return a specific type."; }
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "returnType", "string", "Fully qualified or simple return type name");
    }
    @Override protected List<String> toolRequired() { return req("name", "returnType"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String returnType = arg(request, "returnType");
        var entry = findEntry(name);

        record MethodRef(String declaringClass, String methodName, String params, String retType) {}

        List<MethodRef> results = new ArrayList<>();

        for (var type : entry.model().getAllTypes().stream()
                .sorted(Comparator.comparing(t -> t.getQualifiedName())).toList()) {
            for (var method : type.getMethods()) {
                String ret = method.getType().toString();
                if (ret.equals(returnType) || ret.endsWith("." + returnType)) {
                    String params = method.getParameters().stream()
                            .map(p -> p.getType().toString())
                            .collect(Collectors.joining(", "));
                    results.add(new MethodRef(type.getQualifiedName(), method.getSimpleName(), params, ret));
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Methods returning `").append(returnType).append("`\n\n");
        sb.append("**Total:** ").append(results.size()).append(" methods\n\n");

        for (var r : results) {
            sb.append("- `").append(r.declaringClass).append(".").append(r.methodName)
              .append("(").append(r.params).append(")` → `").append(r.retType).append("`\n");
        }

        if (results.isEmpty()) {
            sb.append("_No methods found._");
        }

        return ok(sb);
    }
}
