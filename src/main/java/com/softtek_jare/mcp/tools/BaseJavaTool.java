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
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek_jare.mcp.ProjectManager;
import com.softtek_jare.mcp.model.ProjectEntry;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.nio.file.Files;
import com.softtek_jare.mcp.model.Fingerprint;
import spoon.reflect.declaration.CtType;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.nio.file.Files;
import com.softtek_jare.mcp.model.Fingerprint;
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
 * Checks that the project is editable; throws if not.
 */
    protected ProjectEntry requireEditable(ProjectEntry entry) {
        if (!entry.editable()) {
            throw new IllegalArgumentException(
                    "Project '" + entry.name() + "' is read-only (loaded from JAR or with editable=false). "
                    + "Edit tools are not available.");
        }
        return entry;
    }

/**
 * Validates the optimistic lock for a file before writing.
 * Implements the 4-condition check from the edit-tools concept Section 5.
 *
 * @param entry              the loaded project entry (in-memory fingerprints)
 * @param file               the target file on disk
 * @param expectedFingerprint optional fingerprint the caller believes the file has;
 *                            null or blank means "not provided"
 * @throws IllegalArgumentException if the file was changed externally or the
 *                                  expected fingerprint does not match
 */
    public static void validateFingerprint(ProjectEntry entry, java.nio.file.Path file, String expectedFingerprint) {
        // Skip disk-vs-fingerprint comparison only for files this session has edited.
        // For unedited files, the fingerprint must still match disk (external change detection).
        if (entry.isFileEdited(file)) return;

        java.nio.file.Path normalized = file.normalize();
        Fingerprint stored = entry.sourceFingerprints().get(normalized);
        if (stored == null) return; // no fingerprint for this file — cannot check

        // Condition 4: in-memory fingerprint must match disk (external change detection)
        if (!Files.exists(file)) {
            throw new IllegalArgumentException(
                    "File '" + file.getFileName() + "' has been deleted since project load. "
                    + "Reload the project before editing.");
        }
        Fingerprint onDisk;
        try {
            onDisk = new Fingerprint(
                    Files.getLastModifiedTime(file).toMillis(),
                    Files.size(file));
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Cannot read file '" + file + "': " + e.getMessage());
        }
        if (!stored.equals(onDisk)) {
            throw new IllegalArgumentException(
                    "File '" + file.getFileName() + "' has been modified since project load (fingerprint mismatch). "
                    + "Another session or external process changed this file. Reload the project before editing.");
        }

        // Conditions 1 & 3: if expectedFingerprint provided, it must match in-memory
        if (expectedFingerprint != null && !expectedFingerprint.isBlank()) {
            String storedStr = fingerprintToString(stored);
            if (!expectedFingerprint.equals(storedStr)) {
                throw new IllegalArgumentException(
                        "File '" + file.getFileName() + "' has been modified since the caller's last observation "
                        + "(expected fingerprint mismatch). Reload the project before editing.");
            }
        }
        // Condition 2: expectedFingerprint not provided, disk matches in-memory — proceed
    }

/**
 * Serializes a fingerprint to a string for use as expectedFingerprint parameter.
 */
    public static String fingerprintToString(Fingerprint fp) {
        return fp.lastModified() + "|" + fp.fileSize();
    }

/**
 * Inserts import statements before the first class/interface/enum declaration in the source.
 */
    protected static String insertImports(String source, List<String> importsToAdd) {
        if (importsToAdd == null || importsToAdd.isEmpty()) return source;
        StringBuilder importBlock = new StringBuilder();
        for (String fqn : importsToAdd) {
            importBlock.append("import ").append(fqn).append(";\n");
        }
        // Find insertion point: after package declaration and existing imports, before first type
        int insertIdx = 0;
        String[] lines = source.split("\n", -1);
        boolean foundPackage = false;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.startsWith("package ")) { foundPackage = true; insertIdx = i + 1; continue; }
            if (t.startsWith("import ")) { insertIdx = i + 1; continue; }
            if (foundPackage || i > 0) {
                // Skip blank lines after package/imports
                if (t.isEmpty() && i == insertIdx) { insertIdx = i + 1; continue; }
                break;
            }
        }
        // Insert import block at insertIdx
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == insertIdx) {
                result.append(importBlock);
                result.append("\n");
            }
            result.append(lines[i]);
            if (i < lines.length - 1) result.append("\n");
        }
        return result.toString();
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
 * Formats a multi-module warning when detected modules exceed loaded modules.
 */
    protected static String formatMultiModuleWarning(ProjectEntry entry) {
        if (entry.modulesDetected() <= entry.modulesLoaded()) return "";
        int unloaded = entry.modulesDetected() - entry.modulesLoaded();
        return "> WARNING: " + entry.modulesDetected() + " modules detected, "
                + entry.modulesLoaded() + " loaded. Edits may miss references in "
                + unloaded + " unloaded module" + (unloaded > 1 ? "s" : "") + ". "
                + "Load all modules before editing for full coverage.\n\n";
    }

/**
 * Formats a model-dirty warning for projects with stale models after edits.
 */
    protected static String formatModelDirtyWarning(ProjectManager manager) {
        List<String> dirty = new ArrayList<>();
        for (var entry : manager.list()) {
            if (entry.modelDirty()) dirty.add(entry.name());
        }
        if (dirty.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("> ⚠️ **Model is dirty:** ");
        for (int i = 0; i < dirty.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("`").append(dirty.get(i)).append("`");
        }
        sb.append("\n> Edits have been made since load. Data may be stale.\n");
        sb.append("> Call `reload_java_project` to refresh the model.\n\n");
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
        warning += formatModelDirtyWarning(manager);
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
