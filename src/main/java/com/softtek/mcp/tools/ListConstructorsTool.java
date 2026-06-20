package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ListConstructorsTool extends BaseJavaTool {

    public ListConstructorsTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "list_constructors"; }
    @Override protected String toolDescription() { return "Lists all constructors in a class with their parameters and bodies."; }
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

        if (!type.isClass()) {
            return error("'" + className + "' is not a class (no constructors).");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Constructors in `").append(className).append("`\n\n");

        var ctClass = (spoon.reflect.declaration.CtClass<?>) type;
        var constructors = ctClass.getConstructors();
        if (constructors.isEmpty()) {
            sb.append("_No explicit constructors (default constructor)._");
            return ok(sb);
        }

        for (var ctor : constructors) {
            String params = ctor.getParameters().stream()
                    .map(p -> p.getType().toString() + " " + p.getSimpleName())
                    .collect(Collectors.joining(", "));
            String vis = ctor.isPublic() ? "public" : ctor.isProtected() ? "protected" : ctor.isPrivate() ? "private" : "package-private";

            sb.append("## `").append(vis).append(" ").append(className).append("(").append(params).append(")`\n\n");

            var annotations = ctor.getAnnotations();
            if (!annotations.isEmpty()) {
                sb.append("Annotations:\n");
                for (var ann : annotations) {
                    sb.append("- `@").append(ann.getAnnotationType().getQualifiedName()).append("`\n");
                }
                sb.append("\n");
            }

            if (ctor.getBody() != null) {
                String body = ctor.getBody().prettyprint();
                sb.append("```java\n").append(body).append("\n```\n\n");
            }
        }

        return ok(sb);
    }
}
