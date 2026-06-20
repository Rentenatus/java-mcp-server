package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.List;
import java.util.Map;

import spoon.reflect.declaration.CtEnum;

public class ListEnumConstantsTool extends BaseJavaTool {

    public ListEnumConstantsTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "list_enum_constants"; }
    @Override protected String toolDescription() { return "Lists all constants (values) of an enum type."; }
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "className", "string", "Fully qualified enum class name");
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
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        if (!type.isEnum()) {
            return error("'" + className + "' is not an enum.");
        }

        CtEnum<?> enumType = (CtEnum<?>) type;
        var values = enumType.getEnumValues();

        StringBuilder sb = new StringBuilder();
        sb.append("# Enum Constants: `").append(className).append("`\n\n");
        sb.append("**Total:** ").append(values.size()).append(" constants\n\n");

        for (var val : values) {
            sb.append("- `").append(val.getSimpleName()).append("`");
            if (val.getDefaultExpression() != null) {
                sb.append(" = ").append(val.getDefaultExpression());
            }
            sb.append("\n");
        }

        return ok(sb);
    }
}
