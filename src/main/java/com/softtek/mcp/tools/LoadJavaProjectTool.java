package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import com.softtek.mcp.ProjectManager;
import com.softtek.mcp.model.ProjectEntry;
import com.softtek.mcp.model.ProjectLoadException;

import java.time.Instant;
import java.util.Map;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public class LoadJavaProjectTool implements McpTool {

    private final ProjectManager manager;
    private static final Logger LOG = LoggerFactory.getLogger(LoadJavaProjectTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public LoadJavaProjectTool(ProjectManager manager) {
        this.manager = manager;
    }

    private String arg(McpSchema.CallToolRequest request, String key) {
        return (String) request.arguments().get(key);
    }

    @Override
    public McpServerFeatures.SyncToolSpecification build() {
        McpSchema.Tool toolDef = McpSchema.Tool.builder("load_java_project")
                .description("Loads a Java project for analysis. Accepts a Git URL, local path, file:// URI, or archive (.zip/.tar.gz).")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "source", Map.of("type", "string",
                                        "description", "Git URL, local path, file:// URI, or archive path"),
                                "alias", Map.of("type", "string",
                                        "description", "Optional alias for referencing the project"),
                                "expiryDate", Map.of("type", "string",
                                        "description", "Optional ISO-8601 expiry date (e.g. 2026-12-31T23:59:59Z)")
                        ),
                        "required", List.of("source")
                ))
                .build();

        return new McpServerFeatures.SyncToolSpecification(toolDef, (exchange, request) -> {
            String source = arg(request, "source");
            String alias = arg(request, "alias");
            String expiryDateStr = arg(request, "expiryDate");

            Instant expiryDate = null;
            if (expiryDateStr != null && !expiryDateStr.isBlank()) {
                try {
                    expiryDate = Instant.parse(expiryDateStr);
                } catch (Exception e) {
                    return buildError("INVALID_PARAMS",
                            "invalid expiryDate format. Expected ISO-8601 (e.g. 2026-12-31T23:59:59Z).");
                }
            }

            LOG.info("Loading Java project: {} (alias={})", source, alias);
            try {
                var entry = manager.load(source, alias, expiryDate);
                String msg = "Java project loaded successfully: " + entry.name()
                        + " (" + entry.model().getAllTypes().size() + " types)"
                        + " [build: " + entry.buildType() + "]";
                return McpSchema.CallToolResult.builder()
                        .addTextContent(msg)
                        .isError(false)
                        .build();
            } catch (ProjectLoadException e) {
                LOG.error("Error loading project: [{}] {}", e.getType(), e.getMessage());
                return buildError(e.getType(), e.getMessage());
            } catch (Exception e) {
                LOG.error("Unexpected error loading project: {}", e.getMessage(), e);
                return buildError("INTERNAL_ERROR", e.getMessage());
            }
        });
    }

    private McpSchema.CallToolResult buildError(String type, String message) {
        try {
            ObjectNode json = MAPPER.createObjectNode();
            json.put("error", true);
            json.put("type", type);
            json.put("message", message != null ? message : "");
            return McpSchema.CallToolResult.builder()
                    .addTextContent(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json))
                    .isError(true)
                    .build();
        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent("Error loading project: " + message)
                    .isError(true)
                    .build();
        }
    }
}
