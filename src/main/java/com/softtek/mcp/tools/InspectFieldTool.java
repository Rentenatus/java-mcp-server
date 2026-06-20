package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.List;
import java.util.Map;

public class InspectFieldTool extends BaseJavaTool {

    public InspectFieldTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "inspect_field"; }
    @Override protected String toolDescription() { return "Deep-dives into a specific field's declaration including type, modifiers, annotations, and initializer."; }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "fieldName", Map.of("type", "string", "description", "Field name")
        );
    }
    @Override protected List<String> toolRequired() { return req("name", "className", "fieldName"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String fieldName = arg(request, "fieldName");
        var entry = findEntry(name);

        var type = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + className));

        var field = type.getFields().stream()
                .filter(f -> f.getSimpleName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Field '" + fieldName + "' not found in " + className));

        StringBuilder sb = new StringBuilder();
        sb.append("# Field: `").append(fieldName).append("` in `").append(className).append("`\n\n");
        sb.append("| Property | Value |\n");
        sb.append("|----------|-------|\n");
        sb.append("| **Type** | `").append(field.getType()).append("` |\n");

        String visibility = field.isPublic() ? "public" : field.isProtected() ? "protected" : field.isPrivate() ? "private" : "package-private";
        sb.append("| **Visibility** | `").append(visibility).append("` |\n");
        if (field.isStatic()) sb.append("| **Static** | ✅ |\n");
        if (field.isFinal()) sb.append("| **Final** | ✅ |\n");
        if (field.isTransient()) sb.append("| **Transient** | ✅ |\n");
        if (field.isVolatile()) sb.append("| **Volatile** | ✅ |\n");

        if (field.getDefaultExpression() != null) {
            sb.append("| **Initializer** | `").append(field.getDefaultExpression()).append("` |\n");
        }

        var annotations = field.getAnnotations();
        if (!annotations.isEmpty()) {
            sb.append("\n## Annotations\n\n");
            for (var ann : annotations) {
                sb.append("- `@").append(ann.getAnnotationType().getQualifiedName()).append("`\n");
            }
        }

        return ok(sb);
    }
}
