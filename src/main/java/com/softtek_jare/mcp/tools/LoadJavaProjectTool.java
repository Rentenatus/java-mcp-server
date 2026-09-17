/*
 * MIT License
 *
 * Copyright (c) 2026 Alejandro Ferreira
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.softtek_jare.mcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import com.softtek_jare.mcp.ProjectManager;
import com.softtek_jare.mcp.model.ProjectEntry;
import com.softtek_jare.mcp.model.ProjectLoadException;

import java.time.Instant;
import java.util.Map;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The {@code LoadJavaProjectTool} class.
 *
 * @author Alejandro Ferreira
 */
public class LoadJavaProjectTool implements McpTool {

    private final ProjectManager manager;
    private static final Logger LOG = LoggerFactory.getLogger(LoadJavaProjectTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

/**
 * Constructs the tool with the given project manager.
 */
    public LoadJavaProjectTool(ProjectManager manager) {
        this.manager = manager;
    }

/**
 * Extracts a string argument from the request.
 */
    private String arg(McpSchema.CallToolRequest request, String key) {
        return (String) request.arguments().get(key);
    }

/**
 * Extracts a boolean argument from the request.
 */
    private Boolean boolArg(McpSchema.CallToolRequest request, String key) {
        Object v = request.arguments().get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return null;
    }

/**
 * Builds the {@code load_java_project} tool specification.
 */
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
                                                + "members in the AST. Set false to load the raw source as-is."),
                                "editable", Map.of("type", "boolean",
                                        "description", "If true (default for local/git/archive), edit tools "
                                                + "are available. JAR sources are always false. Set false "
                                                + "to load in paranoia mode (read-only even on writable filesystem).")
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
            Boolean editableArg = boolArg(request, "editable");

            Instant expiryDate = null;
            if (expiryDateStr != null && !expiryDateStr.isBlank()) {
                try {
                    expiryDate = Instant.parse(expiryDateStr);
                } catch (Exception e) {
                    return buildError("INVALID_PARAMS",
                            "invalid expiryDate format. Expected ISO-8601 (e.g. 2026-12-31T23:59:59Z).");
                }
            }

            LOG.info("Loading Java project: {} (alias={}, delombok={}, editable={})",
                    source, alias, autoDelombok, editableArg);
            try {
                var entry = manager.load(source, alias, expiryDate, autoDelombok, editableArg);
                ObjectNode json = MAPPER.createObjectNode();
                json.put("error", false);
                json.put("message", "Java project loaded successfully");
                json.put("name", entry.name());
                json.put("alias", entry.alias());
                json.put("types", entry.model().getAllTypes().size());
                json.put("build", entry.buildType());
                json.put("delomboked", entry.delomboked());
                json.put("editable", entry.editable());
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

/**
 * Builds a JSON error result with the given type and message.
 */
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
