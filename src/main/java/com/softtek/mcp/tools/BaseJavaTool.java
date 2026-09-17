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

package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;
import com.softtek.mcp.model.ProjectEntry;

import java.util.Map;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code BaseJavaTool} class.
 *
 * @author Alejandro Ferreira
 */
public abstract class BaseJavaTool implements McpTool {

    protected final ProjectManager manager;
    protected final Logger log = LoggerFactory.getLogger(getClass());

/**
 * Constructs the tool with the given project manager.
 */
    protected BaseJavaTool(ProjectManager manager) {
        this.manager = manager;
    }

/**
 * Returns the name of the tool.
 */
    protected abstract String toolName();
/**
 * Returns the human-readable description of the tool.
 */
    protected abstract String toolDescription();
/**
 * Returns the input schema properties for the tool.
 */
    protected abstract Map<String, Object> toolProperties();
/**
 * Returns the list of required argument keys.
 */
    protected abstract List<String> toolRequired();

/**
 * Builds the MCP tool specification with the tool schema and handler.
 */
    @Override
    public McpServerFeatures.SyncToolSpecification build() {
        McpSchema.Tool toolDef = McpSchema.Tool.builder(toolName())
                .description(toolDescription())
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", toolProperties(),
                        "required", toolRequired()
                ))
                .build();

        return new McpServerFeatures.SyncToolSpecification(toolDef, (exchange, request) -> {
            try {
                log.info("Tool invoked: {}({})", toolName(), request.arguments());
                return handle(exchange, request);
            } catch (IllegalArgumentException e) {
                log.warn("Tool {} error: {}", toolName(), e.getMessage());
                return error(e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error in tool {}", toolName(), e);
                return error("internal error: " + e.getMessage());
            }
        });
    }

/**
 * Handles the tool invocation and returns the result.
 */
    protected abstract CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception;

/**
 * Extracts a string argument from the request.
 */
    protected static String arg(CallToolRequest request, String key) {
        return (String) request.arguments().get(key);
    }

/**
 * Extracts a boolean argument from the request, defaulting to {@code false}.
 */
    protected static boolean boolArg(CallToolRequest request, String key) {
        Boolean val = (Boolean) request.arguments().get(key);
        return val != null && val;
    }

/**
 * Finds a loaded project entry by name, throwing if not found.
 */
    protected ProjectEntry findEntry(String name) {
        var entry = manager.find(name);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "no Java project found with name or alias '" + name + "'. Call load_java_project first.");
        }
        return entry;
    }

/**
 * Builds an error result with the given message.
 */
    protected static CallToolResult error(String message) {
        return McpSchema.CallToolResult.builder()
                .addTextContent("Error: " + message)
                .isError(true)
                .build();
    }

/**
 * Builds a successful result with the given text content.
 */
    protected static CallToolResult ok(String content) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(content)
                .isError(false)
                .build();
    }

/**
 * Builds a successful result from a StringBuilder.
 */
    protected static CallToolResult ok(StringBuilder sb) {
        return ok(sb.toString());
    }

/**
 * Builds a single-property schema map.
 */
    protected static Map<String, Object> props(String k1, String t1) {
        return Map.of(k1, Map.of("type", t1));
    }

/**
 * Builds a two-property schema map.
 */
    protected static Map<String, Object> props(String k1, String t1, String k2, String t2) {
        return Map.of(k1, Map.of("type", t1), k2, Map.of("type", t2));
    }

/**
 * Builds a three-property schema map.
 */
    protected static Map<String, Object> props(String k1, String t1, String k2, String t2, String k3, String t3) {
        return Map.of(
            k1, Map.of("type", t1),
            k2, Map.of("type", t2),
            k3, Map.of("type", t3)
        );
    }

/**
 * Builds a single-property schema map with descriptions.
 */
    protected static Map<String, Object> propsWithDescription(String k1, String t1, String d1) {
        return Map.of(k1, Map.of("type", t1, "description", d1));
    }

/**
 * Builds a two-property schema map with descriptions.
 */
    protected static Map<String, Object> propsWithDescription(String k1, String t1, String d1,
                                                                String k2, String t2, String d2) {
        return Map.of(
            k1, Map.of("type", t1, "description", d1),
            k2, Map.of("type", t2, "description", d2)
        );
    }

/**
 * Builds a list of required argument keys.
 */
    protected static List<String> req(String... keys) {
        return List.of(keys);
    }
}
