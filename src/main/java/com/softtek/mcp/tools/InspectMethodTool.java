package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InspectMethodTool extends BaseJavaTool {

    public InspectMethodTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "inspect_method"; }
    @Override protected String toolDescription() { return "Retrieves detailed information about a specific method: signature, parameters, return type, annotations, and source body."; }
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

        var methods = type.getMethods().stream()
                .filter(m -> m.getSimpleName().equals(methodName))
                .toList();

        if (methods.isEmpty()) {
            throw new IllegalArgumentException("Method '" + methodName + "' not found in " + className);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Method: `").append(methodName).append("` in `").append(className).append("`\n\n");

        for (var method : methods) {
            String params = method.getParameters().stream()
                    .map(p -> p.getType().toString() + " " + p.getSimpleName())
                    .collect(Collectors.joining(", "));
            String retType = method.getType().toString();

            sb.append("## Signature\n\n");
            sb.append("```java\n");
            if (method.isPublic()) sb.append("public ");
            else if (method.isProtected()) sb.append("protected ");
            else if (method.isPrivate()) sb.append("private ");
            if (method.isStatic()) sb.append("static ");
            if (method.isAbstract()) sb.append("abstract ");
            sb.append(retType).append(" ").append(methodName).append("(").append(params).append(")");
            sb.append("\n```\n\n");

            sb.append("| Property | Value |\n");
            sb.append("|----------|-------|\n");
            sb.append("| **Return type** | `").append(retType).append("` |\n");
            sb.append("| **Parameters** | ").append(method.getParameters().size()).append(" |\n");
            sb.append("| **Visibility** | ").append(
                method.isPublic() ? "public" :
                method.isProtected() ? "protected" :
                method.isPrivate() ? "private" : "package-private").append(" |\n");

            if (method.isStatic()) sb.append("| **Static** | ✅ |\n");
            if (method.isAbstract()) sb.append("| **Abstract** | ✅ |\n");
            if (method.isFinal()) sb.append("| **Final** | ✅ |\n");

            var annotations = method.getAnnotations();
            if (!annotations.isEmpty()) {
                sb.append("\n## Annotations\n\n");
                for (var ann : annotations) {
                    sb.append("- `@").append(ann.getAnnotationType().getQualifiedName()).append("`\n");
                }
            }

            if (method.getBody() != null) {
                sb.append("\n## Body\n\n```java\n");
                sb.append(method.getBody().prettyprint());
                sb.append("\n```\n");
            } else {
                sb.append("\n_No method body (abstract or native)._");
            }
        }

        return ok(sb);
    }
}
