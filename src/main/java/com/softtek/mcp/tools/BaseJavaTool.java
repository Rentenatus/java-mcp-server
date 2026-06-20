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

public abstract class BaseJavaTool implements McpTool {

    protected final ProjectManager manager;
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected BaseJavaTool(ProjectManager manager) {
        this.manager = manager;
    }

    protected abstract String toolName();
    protected abstract String toolDescription();
    protected abstract Map<String, Object> toolProperties();
    protected abstract List<String> toolRequired();

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

    protected abstract CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception;

    protected static String arg(CallToolRequest request, String key) {
        return (String) request.arguments().get(key);
    }

    protected static boolean boolArg(CallToolRequest request, String key) {
        Boolean val = (Boolean) request.arguments().get(key);
        return val != null && val;
    }

    protected ProjectEntry findEntry(String name) {
        var entry = manager.find(name);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "no Java project found with name or alias '" + name + "'. Call load_java_project first.");
        }
        return entry;
    }

    protected static CallToolResult error(String message) {
        return McpSchema.CallToolResult.builder()
                .addTextContent("Error: " + message)
                .isError(true)
                .build();
    }

    protected static CallToolResult ok(String content) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(content)
                .isError(false)
                .build();
    }

    protected static CallToolResult ok(StringBuilder sb) {
        return ok(sb.toString());
    }

    protected static Map<String, Object> props(String k1, String t1) {
        return Map.of(k1, Map.of("type", t1));
    }

    protected static Map<String, Object> props(String k1, String t1, String k2, String t2) {
        return Map.of(k1, Map.of("type", t1), k2, Map.of("type", t2));
    }

    protected static Map<String, Object> props(String k1, String t1, String k2, String t2, String k3, String t3) {
        return Map.of(
            k1, Map.of("type", t1),
            k2, Map.of("type", t2),
            k3, Map.of("type", t3)
        );
    }

    protected static Map<String, Object> propsWithDescription(String k1, String t1, String d1) {
        return Map.of(k1, Map.of("type", t1, "description", d1));
    }

    protected static Map<String, Object> propsWithDescription(String k1, String t1, String d1,
                                                                String k2, String t2, String d2) {
        return Map.of(
            k1, Map.of("type", t1, "description", d1),
            k2, Map.of("type", t2, "description", d2)
        );
    }

    protected static List<String> req(String... keys) {
        return List.of(keys);
    }
}
