package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InspectClassTool extends BaseJavaTool {

    public InspectClassTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "inspect_class"; }
    @Override protected String toolDescription() { return "Retrieves detailed information about a class: fields, methods, superclass, interfaces, and annotations."; }
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

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(type.getQualifiedName()).append("\n\n");

        String kind;
        if (type.isClass()) kind = "**CLASS**";
        else if (type.isInterface()) kind = "**INTERFACE**";
        else if (type.isEnum()) kind = "**ENUM**";
        else if (type.isAnnotationType()) kind = "**ANNOTATION**";
        else kind = "**TYPE**";
        sb.append("| Property | Value |\n");
        sb.append("|----------|-------|\n");
        sb.append("| **Kind** | ").append(kind).append(" |\n");

        String visibility = type.isPublic() ? "public" : type.isProtected() ? "protected" : type.isPrivate() ? "private" : "package-private";
        sb.append("| **Visibility** | `").append(visibility).append("` |\n");
        if (type.isAbstract()) sb.append("| **Abstract** | ✅ |\n");
        if (type.isFinal()) sb.append("| **Final** | ✅ |\n");
        if (type.isStatic()) sb.append("| **Static** | ✅ |\n");

        if (type.getSuperclass() != null) {
            sb.append("| **Superclass** | `").append(type.getSuperclass().getQualifiedName()).append("` |\n");
        }
        var interfaces = type.getSuperInterfaces();
        if (!interfaces.isEmpty()) {
            String ifaces = interfaces.stream()
                    .map(i -> "`" + i.getQualifiedName() + "`")
                    .collect(Collectors.joining(", "));
            sb.append("| **Interfaces** | ").append(ifaces).append(" |\n");
        }

        sb.append("\n## Fields (").append(type.getFields().size()).append(")\n\n");
        for (var field : type.getFields()) {
            String fVis = field.isPublic() ? "public" : field.isProtected() ? "protected" : field.isPrivate() ? "private" : "";
            sb.append("- `").append(fVis).append(" ").append(field.getType()).append(" ").append(field.getSimpleName()).append("`");
            if (field.isStatic()) sb.append(" `static`");
            if (field.isFinal()) sb.append(" `final`");
            sb.append("\n");
        }

        sb.append("\n## Methods (").append(type.getMethods().size()).append(")\n\n");
        for (var method : type.getMethods()) {
            String params = method.getParameters().stream()
                    .map(p -> p.getType().toString() + " " + p.getSimpleName())
                    .collect(Collectors.joining(", "));
            String mVis = method.isPublic() ? "public" : method.isProtected() ? "protected" : method.isPrivate() ? "private" : "";
            sb.append("- `").append(mVis).append(" ").append(method.getType()).append(" ").append(method.getSimpleName())
              .append("(").append(params).append(")`");
            if (method.isStatic()) sb.append(" `static`");
            if (method.isAbstract()) sb.append(" `abstract`");
            sb.append("\n");
        }

        var annotations = type.getAnnotations();
        if (!annotations.isEmpty()) {
            sb.append("\n## Annotations\n\n");
            for (var ann : annotations) {
                sb.append("- `@").append(ann.getAnnotationType().getQualifiedName()).append("`\n");
            }
        }

        return ok(sb);
    }
}
