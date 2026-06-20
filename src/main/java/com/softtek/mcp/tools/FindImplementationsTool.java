package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.*;

public class FindImplementationsTool extends BaseJavaTool {

    public FindImplementationsTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "find_implementations"; }
    @Override protected String toolDescription() { return "Finds all concrete classes that implement a given interface or extend a given abstract class."; }
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "className", "string", "Fully qualified interface or abstract class name");
    }
    @Override protected List<String> toolRequired() { return req("name", "className"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        var entry = findEntry(name);

        var targetType = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        if (!targetType.isInterface() && !targetType.isAbstract()) {
            return error("'" + className + "' is neither an interface nor an abstract class.");
        }

        List<String> implementations = new ArrayList<>();
        for (var type : entry.model().getAllTypes()) {
            if (type.equals(targetType)) continue;
            if (type.isInterface()) continue;
            if (implementsOrExtends(type, className, entry)) {
                implementations.add(type.getQualifiedName());
            }
        }

        implementations.sort(String::compareTo);

        StringBuilder sb = new StringBuilder();
        sb.append("# Implementations of `").append(className).append("`\n\n");

        if (implementations.isEmpty()) {
            sb.append("_No implementations found._");
        } else {
            sb.append("**Total:** ").append(implementations.size()).append("\n\n");
            for (String impl : implementations) {
                sb.append("- `").append(impl).append("`\n");
            }
        }

        return ok(sb);
    }

    private boolean implementsOrExtends(spoon.reflect.declaration.CtType<?> type, String targetFqn, com.softtek.mcp.model.ProjectEntry entry) {
        var superClass = type.getSuperclass();
        while (superClass != null) {
            if (targetFqn.equals(superClass.getQualifiedName())) return true;
            var superType = findType(entry, superClass.getQualifiedName());
            superClass = superType != null ? superType.getSuperclass() : null;
        }

        for (var iface : type.getSuperInterfaces()) {
            if (targetFqn.equals(iface.getQualifiedName())) return true;
            var ifaceType = findType(entry, iface.getQualifiedName());
            if (ifaceType != null && implementsOrExtends(ifaceType, targetFqn, entry)) return true;
        }

        return false;
    }

    private spoon.reflect.declaration.CtType<?> findType(com.softtek.mcp.model.ProjectEntry entry, String qualifiedName) {
        return entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(qualifiedName))
                .findFirst()
                .orElse(null);
    }
}
