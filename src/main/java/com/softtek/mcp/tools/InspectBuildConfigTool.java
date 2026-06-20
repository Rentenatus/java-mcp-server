package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.util.List;
import java.util.Map;

public class InspectBuildConfigTool extends BaseJavaTool {

    public InspectBuildConfigTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "inspect_build_config"; }
    @Override protected String toolDescription() { return "Shows the detected build configuration for a loaded Java project."; }
    @Override protected Map<String, Object> toolProperties() { return props("name", "string"); }
    @Override protected List<String> toolRequired() { return req("name"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        var entry = findEntry(name);
        var projectDir = entry.projectDir();

        StringBuilder sb = new StringBuilder();
        sb.append("# Build Configuration: ").append(entry.name()).append("\n\n");
        sb.append("| Aspect | Value |\n");
        sb.append("|--------|-------|\n");
        sb.append("| **Build type** | ").append(entry.buildType()).append(" |\n");
        sb.append("| **Project dir** | ").append(projectDir).append(" |\n");

        sb.append("\n## Detected Files\n\n");
        String[] markers = {"pom.xml", "build.gradle", "build.gradle.kts", ".project", ".classpath", "build.xml", "ivy.xml"};
        for (String marker : markers) {
            boolean found = projectDir.resolve(marker).toFile().exists();
            sb.append("- `").append(marker).append("`: ").append(found ? "✅ found" : "—").append("\n");
        }

        return ok(sb);
    }
}
