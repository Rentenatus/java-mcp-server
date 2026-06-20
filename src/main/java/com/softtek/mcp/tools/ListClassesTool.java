package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ListClassesTool extends BaseJavaTool {

    public ListClassesTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "list_classes"; }
    @Override protected String toolDescription() { return "Lists all classes, interfaces, and enums in a loaded Java project, optionally filtered by package."; }
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "package", "string", "Optional: filter by package qualified name");
    }
    @Override protected List<String> toolRequired() { return req("name"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String pkgFilter = arg(request, "package");
        var entry = findEntry(name);

        var types = entry.model().getAllTypes().stream()
                .filter(t -> pkgFilter == null || pkgFilter.isBlank()
                        || (t.getPackage() != null && pkgFilter.equals(t.getPackage().getQualifiedName())))
                .sorted(Comparator.comparing(t -> t.getQualifiedName()))
                .toList();

        StringBuilder sb = new StringBuilder("# Classes");
        if (pkgFilter != null && !pkgFilter.isBlank()) {
            sb.append(" in `").append(pkgFilter).append("`");
        }
        sb.append(" (").append(entry.name()).append(")\n\n");
        sb.append("**Total:** ").append(types.size()).append(" types\n\n");

        for (var type : types) {
            String kind;
            if (type.isClass()) kind = "CLASS";
            else if (type.isInterface()) kind = "INTERFACE";
            else if (type.isEnum()) kind = "ENUM";
            else if (type.isAnnotationType()) kind = "ANNOTATION";
            else kind = "TYPE";

            sb.append("- **").append(type.getQualifiedName()).append("**");
            sb.append(" (").append(kind).append(", ").append(type.getMethods().size()).append(" methods)");
            if (type.isPublic()) sb.append(" `public`");
            sb.append("\n");
        }

        return ok(sb);
    }
}
