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
import java.util.ArrayList;
import java.util.Objects;
import java.nio.file.Files;
import com.softtek.mcp.model.Fingerprint;
import spoon.reflect.declaration.CtType;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.nio.file.Files;
import com.softtek.mcp.model.Fingerprint;
import spoon.reflect.declaration.CtType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code BaseJavaTool} class.
 *
 * @author Alejandro Ferreira
 * @author Janusch Rentenatus
 */
public abstract class BaseJavaTool implements McpTool {

    protected final ProjectManager manager;
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private List<String> expiredNames = List.of();

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
                expiredNames = manager.markExpired();
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
 * Formats an expiry warning for newly expired projects.
 */
    protected static String formatExpiredWarning(List<String> expiredNames) {
        if (expiredNames == null || expiredNames.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("> ⚠️ **Expired projects:** ");
        for (int i = 0; i < expiredNames.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("`").append(expiredNames.get(i)).append("`");
        }
        sb.append("\n");
        sb.append("> These projects have passed their expiry date. Data may be stale.\n");
        sb.append("> Call `reload_java_project` with `expired=true` to reload all expired projects.\n\n");
        return sb.toString();
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
    protected CallToolResult ok(String content) {
        String warning = formatExpiredWarning(expiredNames);
        return McpSchema.CallToolResult.builder()
                .addTextContent(warning + content)
                .isError(false)
                .build();
    }

/**
 * Builds a successful result from a StringBuilder.
 */
    protected CallToolResult ok(StringBuilder sb) {
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
 * Builds a three-property schema map with descriptions.
 */
    protected static Map<String, Object> propsWithDescription(String k1, String t1, String d1,
                                                                String k2, String t2, String d2,
                                                                String k3, String t3, String d3) {
        return Map.of(
            k1, Map.of("type", t1, "description", d1),
            k2, Map.of("type", t2, "description", d2),
            k3, Map.of("type", t3, "description", d3)
        );
    }

/**
 * Builds a list of required argument keys.
 */
    protected static List<String> req(String... keys) {
        return List.of(keys);
    }

/**
 * Result of a dirty check: how many files were checked and which changed.
 */
    protected record DirtyCheckResult(int filesChecked, List<String> changedFiles, List<String> deletedFiles, List<String> newFiles) {
        boolean isDirty() { return !changedFiles.isEmpty() || !deletedFiles.isEmpty() || !newFiles.isEmpty(); }
    }

/**
 * Scoped dirty check: compares fingerprints only for the source files of the given types.
 */
    protected static DirtyCheckResult checkDirty(ProjectEntry entry, List<CtType<?>> typesToCheck) {
        List<String> changed = new ArrayList<>();
        List<java.nio.file.Path> sourceFiles = typesToCheck.stream()
            .map(t -> t.getPosition().getFile() != null ? t.getPosition().getFile().toPath() : null)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        for (java.nio.file.Path file : sourceFiles) {
            Fingerprint stored = entry.sourceFingerprints().get(file);
            if (stored == null) continue;
            if (!Files.exists(file)) { changed.add(file + " (deleted)"); continue; }
            try {
                Fingerprint current = new Fingerprint(
                    Files.getLastModifiedTime(file).toMillis(),
                    Files.size(file));
                if (!current.equals(stored)) changed.add(file.toString());
            } catch (java.io.IOException e) {
                changed.add(file + " (read error)");
            }
        }
        return new DirtyCheckResult(sourceFiles.size(), changed, List.of(), List.of());
    }

/**
 * Full-scan dirty check: walks the entire source root, detecting changed, deleted, and new files.
 */
    protected static DirtyCheckResult checkDirtyFullScan(ProjectEntry entry) {
        List<String> changed = new ArrayList<>();
        List<String> deleted = new ArrayList<>();
        List<String> newFiles = new ArrayList<>();
        java.nio.file.Path root = entry.originalProjectDir();
        if (root == null || !Files.isDirectory(root)) {
            return new DirtyCheckResult(0, changed, deleted, newFiles);
        }
        java.util.Set<java.nio.file.Path> onDisk = new java.util.HashSet<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".java"))
                  .forEach(p -> {
                      java.nio.file.Path norm = p.normalize();
                      onDisk.add(norm);
                      Fingerprint stored = entry.sourceFingerprints().get(norm);
                      if (stored == null) { newFiles.add(norm.toString()); return; }
                      try {
                          Fingerprint current = new Fingerprint(
                              Files.getLastModifiedTime(norm).toMillis(),
                              Files.size(norm));
                          if (!current.equals(stored)) changed.add(norm.toString());
                      } catch (java.io.IOException e) {
                          changed.add(norm + " (read error)");
                      }
                  });
        } catch (java.io.IOException e) {
            // walk failed — return empty
        }
        for (java.nio.file.Path stored : entry.sourceFingerprints().keySet()) {
            if (!onDisk.contains(stored)) deleted.add(stored.toString());
        }
        return new DirtyCheckResult(onDisk.size(), changed, deleted, newFiles);
    }

/**
 * Formats a scoped dirty warning (no clean confirmation — token efficiency).
 */
    protected static String formatDirtyWarning(DirtyCheckResult dirty) {
        if (!dirty.isDirty()) return "";
        StringBuilder sb = new StringBuilder();
        int total = dirty.changedFiles().size() + dirty.deletedFiles().size() + dirty.newFiles().size();
        sb.append("> ⚠️ ").append(total).append(" source file(s) changed since load:\n");
        List<String> all = new ArrayList<>();
        all.addAll(dirty.changedFiles());
        all.addAll(dirty.deletedFiles());
        all.addAll(dirty.newFiles());
        int cap = Math.min(all.size(), 10);
        for (int i = 0; i < cap; i++) {
            sb.append("> ").append(all.get(i)).append("\n");
        }
        if (all.size() > 10) sb.append("> …and ").append(all.size() - 10).append(" more\n");
        sb.append("> Call `reload_java_project` to refresh the model.\n\n");
        return sb.toString();
    }
}
