package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class GetFileContentTool extends BaseJavaTool {

    public GetFileContentTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "get_file_content"; }
    @Override protected String toolDescription() { return "Reads the raw source content of the file containing a given type."; }
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
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        Path filePath = null;

        if (type.getPosition() != null && type.getPosition().getFile() != null) {
            filePath = type.getPosition().getFile().toPath().normalize();
        }

        if (filePath == null || !Files.exists(filePath)) {
            String packagePath = type.getPackage() != null
                    ? type.getPackage().getQualifiedName().replace('.', '/')
                    : "";
            Path expected = entry.projectDir().resolve("src/main/java")
                    .resolve(packagePath)
                    .resolve(type.getSimpleName() + ".java");
            if (Files.exists(expected)) {
                filePath = expected;
            }
        }

        if (filePath == null || !Files.exists(filePath)) {
            return error("Could not locate source file for " + className);
        }

        try {
            String content = Files.readString(filePath);
            StringBuilder sb = new StringBuilder();
            sb.append("### File: ").append(filePath).append("\n\n");
            sb.append("```java\n").append(content);
            if (!content.endsWith("\n")) {
                sb.append("\n");
            }
            sb.append("```");
            return ok(sb);
        } catch (Exception e) {
            return error("Failed to read file: " + e.getMessage());
        }
    }
}
