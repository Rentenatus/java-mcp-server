package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.List;
import java.util.Map;

import spoon.reflect.declaration.CtAnnotationMethod;
import spoon.reflect.declaration.CtAnnotationType;

public class GetAnnotationDetailsTool extends BaseJavaTool {

    public GetAnnotationDetailsTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "get_annotation_details"; }
    @Override protected String toolDescription() { return "Retrieves the definition of an annotation type including its attributes/elements."; }
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "className", "string", "Fully qualified annotation class name");
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
                .orElseThrow(() -> new IllegalArgumentException("Annotation type not found: " + className));

        if (!type.isAnnotationType()) {
            return error("'" + className + "' is not an annotation type.");
        }

        CtAnnotationType<?> annType = (CtAnnotationType<?>) type;

        StringBuilder sb = new StringBuilder();
        sb.append("# Annotation: `@").append(className).append("`\n\n");

        var elements = annType.getMethods();
        if (elements.isEmpty()) {
            sb.append("_Marker annotation (no elements)._");
            return ok(sb);
        }

        sb.append("**Elements:** ").append(elements.size()).append("\n\n");
        for (var elem : elements) {
            String retType = elem.getType().toString();
            String defaultVal = "";
            if (elem instanceof CtAnnotationMethod<?> annMethod && annMethod.getDefaultExpression() != null) {
                defaultVal = " default " + annMethod.getDefaultExpression();
            }
            sb.append("- `").append(retType).append(" ").append(elem.getSimpleName())
              .append("()").append(defaultVal).append("`\n");
        }

        return ok(sb);
    }
}
