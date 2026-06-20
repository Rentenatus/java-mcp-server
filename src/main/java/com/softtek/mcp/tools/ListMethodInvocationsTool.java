package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.*;
import java.util.stream.Collectors;

import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

public class ListMethodInvocationsTool extends BaseJavaTool {

    public ListMethodInvocationsTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "list_method_invocations"; }
    @Override protected String toolDescription() { return "Lists all method calls made within a given method's body."; }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "methodName", Map.of("type", "string", "description", "Method name")
        );
    }
    @Override protected List<String> toolRequired() { return req("name", "className", "methodName"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String methodName = arg(request, "methodName");
        var entry = findEntry(name);

        var type = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + className));

        var method = type.getMethods().stream()
                .filter(m -> m.getSimpleName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Method '" + methodName + "' not found in " + className));

        StringBuilder sb = new StringBuilder();
        sb.append("# Method Invocations in `").append(className).append(".").append(methodName).append("`\n\n");

        if (method.getBody() == null) {
            sb.append("_No method body (abstract or native)._");
            return ok(sb);
        }

        List<CtInvocation<?>> invocations = method.getBody().getElements(new TypeFilter<>(CtInvocation.class));

        if (invocations.isEmpty()) {
            sb.append("_No method invocations found._");
            return ok(sb);
        }

        sb.append("**Total calls:** ").append(invocations.size()).append("\n\n");

        Map<String, Integer> callCounts = new LinkedHashMap<>();
        for (var inv : invocations) {
            var exec = inv.getExecutable();
            String targetType = exec.getDeclaringType() != null
                    ? exec.getDeclaringType().getQualifiedName()
                    : "(unknown)";
            String callKey = targetType + "." + exec.getSimpleName() + "(...)";
            callCounts.merge(callKey, 1, Integer::sum);
        }

        for (var e : callCounts.entrySet()) {
            sb.append("- `").append(e.getKey()).append("` (x").append(e.getValue()).append(")\n");
        }

        return ok(sb);
    }
}
