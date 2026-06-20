package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class ListDependenciesTool extends BaseJavaTool {

    public ListDependenciesTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "list_dependencies"; }
    @Override protected String toolDescription() { return "Lists external dependencies of a loaded Java project by scanning imports."; }
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "group", "string", "Optional: filter by group/package prefix (e.g. 'org.springframework')");
    }
    @Override protected List<String> toolRequired() { return req("name"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String groupFilter = arg(request, "group");
        var entry = findEntry(name);

        Set<String> packages = new TreeSet<>();
        for (var type : entry.model().getAllTypes()) {
            type.getReferencedTypes().forEach(ref -> {
                String qn = ref.getQualifiedName();
                int dot = qn.lastIndexOf('.');
                if (dot > 0) {
                    String pkg = qn.substring(0, dot);
                    if (!pkg.startsWith("java.") && !pkg.startsWith("javax.")) {
                        packages.add(pkg);
                    }
                }
            });
        }

        StringBuilder sb = new StringBuilder("# Dependencies (").append(entry.name()).append(")\n\n");

        Set<String> displayPackages = packages;
        if (groupFilter != null && !groupFilter.isBlank()) {
            displayPackages = packages.stream()
                    .filter(p -> p.startsWith(groupFilter))
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        sb.append("**Total external packages referenced:** ").append(displayPackages.size()).append("\n\n");

        for (String pkg : displayPackages) {
            sb.append("- `").append(pkg).append("`\n");
        }

        return ok(sb);
    }
}
