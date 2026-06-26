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

    private Boolean boolArg(McpSchema.CallToolRequest request, String key) {
        Object v = request.arguments().get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return null;
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
                                        "description", "Optional ISO-8601 expiry date (e.g. 2026-12-31T23:59:59Z)"),
                                "delombok", Map.of("type", "boolean",
                                        "description", "If true (default) and Lombok is detected, "
                                                + "automatically run delombok to expose Lombok-generated "
                                                + "members in the AST. Set false to load the raw source as-is.")
                        ),
                        "required", List.of("source")
                ))
                .build();

        return new McpServerFeatures.SyncToolSpecification(toolDef, (exchange, request) -> {
            String source = arg(request, "source");
            String alias = arg(request, "alias");
            String expiryDateStr = arg(request, "expiryDate");
            Boolean delombokArg = boolArg(request, "delombok");
            boolean autoDelombok = delombokArg == null || delombokArg;

            Instant expiryDate = null;
            if (expiryDateStr != null && !expiryDateStr.isBlank()) {
                try {
                    expiryDate = Instant.parse(expiryDateStr);
                } catch (Exception e) {
                    return buildError("INVALID_PARAMS",
                            "invalid expiryDate format. Expected ISO-8601 (e.g. 2026-12-31T23:59:59Z).");
                }
            }

            LOG.info("Loading Java project: {} (alias={}, delombok={})", source, alias, autoDelombok);
            try {
                var entry = manager.load(source, alias, expiryDate, autoDelombok);
                ObjectNode json = MAPPER.createObjectNode();
                json.put("error", false);
                json.put("message", "Java project loaded successfully");
                json.put("name", entry.name());
                json.put("alias", entry.alias());
                json.put("types", entry.model().getAllTypes().size());
                json.put("build", entry.buildType());
                json.put("delomboked", entry.delomboked());
                if (entry.lombokVersion() != null) {
                    json.put("lombokVersion", entry.lombokVersion());
                }
                if (entry.delomboked()) {
                    json.put("note", "Lombok-generated members are now visible in the AST. "
                            + "If you set delombok=false, getters/setters/equals/etc. are absent from the model.");
                }
                return McpSchema.CallToolResult.builder()
                        .addTextContent(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json))
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
