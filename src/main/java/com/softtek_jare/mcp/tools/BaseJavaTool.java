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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.nio.file.Files;
import com.softtek_jare.mcp.model.Fingerprint;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtField;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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
                log.warn("Tool {} domain error: {}", toolName(), e.getMessage());
                return domainError("DOMAIN_ERROR", e.getMessage(), (Map<String, Object>) null);
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
        // P51: deduplicate — skip imports already present in the source
        List<String> filtered = new ArrayList<>();
        for (String fqn : importsToAdd) {
            String importLine = "import " + fqn + ";";
            if (!source.contains(importLine)) {
                filtered.add(fqn);
            }
        }
        if (filtered.isEmpty()) return source;
        StringBuilder importBlock = new StringBuilder();
        for (String fqn : filtered) {
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
 * Lists the per-file dirty set (file names, not full paths) so the agent can
 * see exactly which source files are stale. Capped at 5 files per project.
 */
    protected static String formatModelDirtyWarning(ProjectManager manager) {
        List<String> dirty = new ArrayList<>();
        java.util.Map<String, List<String>> dirtyFilesByProject = new java.util.LinkedHashMap<>();
        for (var entry : manager.list()) {
            if (entry.modelDirty()) {
                dirty.add(entry.name());
                List<String> names = new ArrayList<>();
                for (java.nio.file.Path p : entry.dirtyFiles()) {
                    java.nio.file.Path fn = p.getFileName();
                    names.add(fn != null ? fn.toString() : p.toString());
                }
                dirtyFilesByProject.put(entry.name(), names);
            }
        }
        if (dirty.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("> ⚠️ **Model is dirty:** ");
        for (int i = 0; i < dirty.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("`").append(dirty.get(i)).append("`");
        }
        sb.append("\n> Edits have been made since load. Data may be stale.\n");
        for (var proj : dirtyFilesByProject.entrySet()) {
            List<String> files = proj.getValue();
            sb.append("> `").append(proj.getKey()).append("` — ");
            if (files.size() <= 5) {
                sb.append(String.join(", ", files));
            } else {
                sb.append(files.size()).append(" files (");
                sb.append(String.join(", ", files.subList(0, 5)));
                sb.append(", ...)");
            }
            sb.append("\n");
        }
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
     * Maximum number of stale (dirty) source files that reference-needing
     * edit tools (global rename, signature+callers) will re-parse on demand
     * for caller detection before requiring a full project reload. Above this
     * threshold, per-file fresh-parsing becomes too expensive and the tool
     * returns a domain error pointing the agent at {@code reload_java_project}.
     */
    protected static final int STALE_FILE_THRESHOLD = 20;

    /**
     * Guards reference-needing operations against an excessively stale model.
     * Returns a domain-error result the caller should return immediately when
     * too many source files have changed since load, or {@code null} to proceed.
     * Position-only edits (add_method, add_field, edit_line, ...) are NOT
     * affected — they re-parse their single target file on demand and do not
     * need the model's cross-file reference graph.
     */
    protected CallToolResult guardStaleThreshold(ProjectEntry entry) {
        if (entry.dirtyFiles().size() > STALE_FILE_THRESHOLD) {
            return domainError("MODEL_STALE",
                    entry.dirtyFiles().size() + " source files have changed since load — too many to re-parse "
                    + "individually for caller detection. The in-memory reference graph is stale.",
                    ctx("staleFiles", String.valueOf(entry.dirtyFiles().size()),
                        "threshold", String.valueOf(STALE_FILE_THRESHOLD),
                        "suggestion", "Call reload_java_project to refresh the model, then retry the edit."));
        }
        return null;
    }

    /**
     * Builds a map from normalized source-file path to a freshly parsed
     * {@link CtType} for each file in the entry's {@code dirtyFiles}. Reference-
     * needing tools use this to scan callers in stale files accurately without
     * a full project reload: for each type whose source file is dirty, the
     * fresh-parsed type replaces the stale model type during the caller scan.
     * Returns an empty map when no files are dirty. Callers should first pass
     * {@link #guardStaleThreshold(ProjectEntry)} to avoid re-parsing too many.
     */
    protected static java.util.Map<java.nio.file.Path, CtType<?>> freshTypesForDirtyFiles(ProjectEntry entry) {
        java.util.Map<java.nio.file.Path, CtType<?>> fresh = new java.util.HashMap<>();
        if (entry.dirtyFiles().isEmpty()) return fresh;
        // Canonicalize dirty paths so matching tolerates path-form differences
        // (Windows 8.3 short names, symlinks) between the dirty set (populated
        // from writeFile, which may receive an agent-supplied path) and the
        // model's reported file paths (resolved by Spoon).
        java.util.Set<String> dirtyCanonical = new java.util.HashSet<>();
        for (java.nio.file.Path dirty : entry.dirtyFiles()) {
            dirtyCanonical.add(canonicalizePath(dirty));
        }
        for (CtType<?> t : entry.model().getAllTypes()) {
            if (t.getPosition() == null || t.getPosition().getFile() == null) continue;
            java.nio.file.Path modelFile = t.getPosition().getFile().toPath().normalize();
            if (!dirtyCanonical.contains(canonicalizePath(modelFile))) continue;
            CtType<?> ft = locateFreshType(modelFile, t.getQualifiedName());
            if (ft != null) {
                // Key by the model's normalized file path: the caller loops look
                // up via type.getPosition().getFile().toPath().normalize(), so
                // this key form matches their lookup.
                fresh.put(modelFile, ft);
            }
        }
        return fresh;
    }

    /**
     * Canonicalizes a path to a real-path string for tolerant comparison,
     * falling back to the absolute normalized string when the file does not
     * exist (e.g. deleted) or cannot be resolved.
     */
    private static String canonicalizePath(java.nio.file.Path p) {
        try {
            return p.toRealPath().toString();
        } catch (java.io.IOException e) {
            return p.toAbsolutePath().normalize().toString();
        }
    }

    /**
     * Formats a one-line advisory noting that stale files were re-parsed on
     * demand for caller detection, so the agent knows the model is only
     * partially refreshed and a full reload is still needed eventually.
     */
    protected static String formatFreshParsedWarning(java.util.Map<java.nio.file.Path, CtType<?>> freshTypes) {
        if (freshTypes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("> ⚠️ ").append(freshTypes.size()).append(" stale file(s) re-parsed on demand for caller detection: ");
        int i = 0;
        for (var e : freshTypes.entrySet()) {
            if (i > 0) sb.append(", ");
            java.nio.file.Path fn = e.getKey().getFileName();
            sb.append(fn != null ? fn : e.getKey());
            if (++i >= 5) { sb.append(", …"); break; }
        }
        sb.append(".\n> Model is partially stale — call `reload_java_project` for a full refresh.\n\n");
        return sb.toString();
    }

    private static final ObjectMapper DOMAIN_ERROR_MAPPER = new ObjectMapper();

    /**
     * Builds a <strong>domain error</strong> result — a structured, agent-readable
     * answer for a <em>fachlichen</em> Fehler (Spoon parse error, type not found,
     * duplicate annotation, invalid {@code @Override}, fingerprint mismatch, etc.).
     *
     * <p>Grundsatz: Kein Fehler von Spoon, Java-Parsen oder Java-Schreiben darf
     * als edit-tool-fehler ({@code isError=true}) zurückkommen. Domain errors
     * return {@code isError=false} with a JSON payload so the agent can inspect
     * the attributes and decide what to do next.
     *
     * <p>The JSON payload contains:
     * <ul>
     *   <li>{@code error} — always {@code false} (not a protocol error)</li>
     *   <li>{@code isDomainError} — always {@code true}</li>
     *   <li>{@code errorType} — a stable enum-like string (e.g. {@code "TYPE_NOT_FOUND"},
     *       {@code "DUPLICATE_ANNOTATION"}, {@code "OVERRIDE_INVALID"})</li>
     *   <li>{@code message} — human-readable description</li>
     *   <li>{@code tool} — the tool that produced the error</li>
     *   <li>any keys from {@code context} — e.g. {@code className}, {@code methodName},
     *       {@code parameters}, {@code details}, {@code suggestion}</li>
     * </ul>
     *
     * @param errorType a stable, uppercase error category
     * @param message   human-readable description of the problem
     * @param context   additional attributes (className, methodName, parameters,
     *                  details, suggestion, etc.); may be {@code null}
     * @return a {@code CallToolResult} with {@code isError=false} and JSON content
     */
    protected CallToolResult domainError(String errorType, String message, Map<String, Object> context) {
        try {
            ObjectNode json = DOMAIN_ERROR_MAPPER.createObjectNode();
            json.put("error", false);
            json.put("isDomainError", true);
            json.put("errorType", errorType != null ? errorType : "UNKNOWN");
            json.put("message", message != null ? message : "");
            json.put("tool", toolName());
            if (context != null) {
                for (var entry : context.entrySet()) {
                    String k = entry.getKey();
                    Object v = entry.getValue();
                    if (v == null) json.putNull(k);
                    else if (v instanceof String s) json.put(k, s);
                    else if (v instanceof Boolean b) json.put(k, b);
                    else json.put(k, v.toString());
                }
            }
            String content = DOMAIN_ERROR_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            log.warn("Domain error [{}]: {} | context={}", errorType, message, context);
            return McpSchema.CallToolResult.builder()
                    .addTextContent(content)
                    .isError(false)
                    .build();
        } catch (Exception e) {
            log.warn("Domain error fallback [{}]: {}", errorType, message);
            return McpSchema.CallToolResult.builder()
                    .addTextContent("Domain error [" + errorType + "]: " + message)
                    .isError(false)
                    .build();
        }
    }

    /**
     * Convenience overload: domain error with errorType and message, no context.
     */
    protected CallToolResult domainError(String errorType, String message) {
        return domainError(errorType, message, (Map<String, Object>) null);
    }

    /**
     * Convenience overload for a domain error with a single className context.
     */
    protected CallToolResult domainError(String errorType, String message, String className) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        if (className != null) ctx.put("className", className);
        return domainError(errorType, message, ctx);
    }

    /**
     * Convenience overload: builds a context map from key-value pairs.
     * Usage: {@code domainError("TYPE_NOT_FOUND", "...", ctx("className", cls, "methodName", m))}
     */
    protected static Map<String, Object> ctx(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            m.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return m;
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
 * Computes the 1-indexed start line of the annotation/comment block that
 * immediately precedes the given declaration line. Annotations sit above the
 * declaration, so a search window that starts at the declaration line misses
 * them. This walks upward over contiguous annotation and comment lines
 * (stopping at a blank line or a non-annotation line) and returns the first
 * line of that block, or {@code declLine} if there is none.
 *
 * @param lines    the source split into lines (any line ending already stripped)
 * @param declLine the 1-indexed declaration line
 * @return the 1-indexed start line of the preceding annotation block
 */
    protected static int annotationSearchStart(String[] lines, int declLine) {
        int start = declLine;
        int parenDepth = 0; // >0 means we are inside a multi-line annotation
        for (int i = declLine - 2; i >= 0; i--) {
            String t = lines[i].trim();
            if (parenDepth <= 0 && t.isEmpty()) break;
            boolean isPattern = t.startsWith("@") || t.startsWith("//") || t.startsWith("*")
                    || t.startsWith("/*") || t.startsWith("*/");
            if (!isPattern && parenDepth <= 0) {
                // Check if this could be the closing ')' of a multi-line annotation
                int net = countNetParens(lines[i]);
                if (net > 0) {
                    parenDepth = net;
                    start = i + 1;
                    continue;
                }
                break;
            }
            start = i + 1;
            parenDepth += countNetParens(lines[i]);
            if (parenDepth < 0) parenDepth = 0;
        }
        return start;
    }

    /**
     * Counts net parentheses (closing minus opening) in a line, skipping
     * string and char literals. Positive = more closing parens than opening.
     */
    static int countNetParens(String line) {
        int net = 0;
        boolean inStr = false, inChar = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inStr) {
                if (c == '\\') { i++; continue; }
                if (c == '"') inStr = false;
                continue;
            }
            if (inChar) {
                if (c == '\\') { i++; continue; }
                if (c == '\'') inChar = false;
                continue;
            }
            if (c == '"') { inStr = true; continue; }
            if (c == '\'') { inChar = true; continue; }
            if (c == '(') net--;
            else if (c == ')') net++;
        }
        return net;
    }

    /**
     * Finds {@code @annotation} (optionally with a parenthesized argument list)
     * within the character range {@code [startOffset, endOffset)} of {@code source}
     * and replaces it with {@code replacement}. Handles multi-line annotations
     * and nested parentheses inside string/char-aware scanning. Returns the
     * modified source, or {@code null} if the annotation was not found.
     */
    protected static String replaceAnnotationInWindow(String source, int startOffset, int endOffset,
                                                       String annotation, String replacement) {
        String search = "@" + annotation;
        int windowLen = endOffset - startOffset;
        int atIdx = -1;
        for (int i = startOffset; i <= endOffset - search.length(); i++) {
            if (source.regionMatches(i, search, 0, search.length())) {
                // Whole-word check: preceding char must not be an identifier part
                if (i > startOffset && Character.isJavaIdentifierPart(source.charAt(i - 1))) continue;
                // Following char must not be an identifier part (avoids @Override matching @Overrides)
                int after = i + search.length();
                if (after < endOffset && Character.isJavaIdentifierPart(source.charAt(after))) continue;
                atIdx = i;
                break;
            }
        }
        if (atIdx < 0) return null;

        int afterName = atIdx + search.length();
        // Skip whitespace
        int j = afterName;
        while (j < endOffset && Character.isWhitespace(source.charAt(j))) j++;

        int replaceEnd;
        if (j < endOffset && source.charAt(j) == '(') {
            // Find matching ')' with depth tracking, skipping strings/chars
            int close = findMatchingParen(source, j, endOffset);
            if (close < 0) return null; // unmatched — abort
            replaceEnd = close + 1;
        } else {
            replaceEnd = afterName;
        }
        return source.substring(0, atIdx) + replacement + source.substring(replaceEnd);
    }

    /** Returns the index of the ')' matching the '(' at {@code open}, or -1. */
    private static int findMatchingParen(String s, int open, int limit) {
        int depth = 0;
        int i = open;
        boolean inStr = false, inChar = false;
        while (i < limit) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\') { i += 2; continue; }
                if (c == '"') inStr = false;
                i++; continue;
            }
            if (inChar) {
                if (c == '\\') { i += 2; continue; }
                if (c == '\'') inChar = false;
                i++; continue;
            }
            if (c == '"') { inStr = true; i++; continue; }
            if (c == '\'') { inChar = true; i++; continue; }
            if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) return i; }
            i++;
        }
        return -1;
    }

    /**
     * Returns the index of the ')' matching the '(' at {@code open} within a
     * single line, skipping string and char literals, or -1. Single-line
     * wrapper around the offset-based scanner, shared by tools that match a
     * parameter list on one declaration line.
     */
    protected static int findMatchingParen(String line, int open) {
        return findMatchingParen(line, open, line.length());
    }

    /**
     * Finds the offset of the class closing brace by searching for the last
     * top-level {@code '}'} in the source. Scans backward from the end of the
     * file, skipping {@code '}'} characters inside comments and string/char
     * literals (P49). The classEndLine hint from the model is used as a
     * starting hint but the comment-aware scan from the end is the
     * authoritative source, making this robust against stale positions (P41).
     */
    protected static int findClassClosingBrace(String source, int classEndLine) {
        // P49: scan backward from end, skip } in comments/strings
        int i = source.length() - 1;
        boolean inBlock = false;
        while (i >= 0) {
            char c = source.charAt(i);
            // Check for block comment end */ (scanning backward, we see */ before /*)
            if (inBlock) {
                if (c == '*' && i > 0 && source.charAt(i - 1) == '/') { inBlock = false; i -= 2; continue; }
                i--; continue;
            }
            // Check for block comment start (from backward perspective: */ ... /*)
            if (c == '/' && i > 0 && source.charAt(i - 1) == '*') { inBlock = true; i -= 2; continue; }
            // Check for line comment // — skip to beginning of line
            if (c == '/' && i > 0 && source.charAt(i - 1) == '/') {
                int nl = source.lastIndexOf('\n', i);
                i = (nl < 0) ? -1 : nl;
                continue;
            }
            // Skip string/char literals (scan backward to find the opening quote)
            if (c == '"') {
                i = skipStringBackward(source, i);
                continue;
            }
            if (c == '\'') {
                i = skipCharBackward(source, i);
                continue;
            }
            if (c == '}') return i;
            i--;
        }
        return -1;
    }

    /** Scans backward from a closing '"' to just before the opening '"'. */
    private static int skipStringBackward(String s, int close) {
        int i = close - 1;
        while (i >= 0) {
            char c = s.charAt(i);
            if (c == '\\') { i--; continue; } // escaped char
            if (c == '"') return i - 1;
            i--;
        }
        return -1;
    }

    /** Scans backward from a closing '\'' to just before the opening '\''. */
    private static int skipCharBackward(String s, int close) {
        int i = close - 1;
        while (i >= 0) {
            char c = s.charAt(i);
            if (c == '\\') { i--; continue; }
            if (c == '\'') return i - 1;
            i--;
        }
        return -1;
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

    /**
     * Splits a comma-separated list on top-level commas only, keeping commas
     * inside generic angle brackets together. For example {@code "Map<K, V>, int"}
     * yields {@code ["Map<K, V>", "int"]}. Each fragment is trimmed. Shared by
     * tools that parse method signatures or parameter lists.
     */
    protected static List<String> splitTopLevelCommas(String list) {
        List<String> params = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < list.length(); i++) {
            char c = list.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth = Math.max(0, depth - 1);
            if (c == ',' && depth == 0) {
                params.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) params.add(cur.toString().trim());
        return params;
    }

    // --- Shared annotation search-window helper (used by edit/remove annotation tools) ---

    /**
     * Validates an annotation target specification shared by the add, edit,
     * and remove annotation tools.
     *
     * @param targetType {@code "class"}, {@code "method"}, or {@code "field"}
     * @param targetName method or field name (null/blank for class)
     * @return an error message if invalid, or {@code null} if valid
     */
    protected static String validateAnnotationTarget(String targetType, String targetName) {
        if (!"class".equals(targetType) && !"method".equals(targetType) && !"field".equals(targetType)) {
            return "targetType must be 'class', 'method', or 'field'";
        }
        if (("method".equals(targetType) || "field".equals(targetType))
                && (targetName == null || targetName.isBlank())) {
            return "targetName required for method and field annotations";
        }
        return null;
    }

    /**
     * Computes the character offset range {@code [startOffset, endOffset)} for
     * the annotation block preceding the declaration of the given target. The
     * window includes the annotation block (which sits above the declaration)
     * up to and including the declaration line itself.
     *
     * @param source     the full source text of the file
     * @param type       the CtType containing the target
     * @param targetType {@code "class"}, {@code "method"}, or {@code "field"}
     * @param targetName method or field name (null for class)
     * @return {@code int[]} of {@code [startOffset, endOffset]}, or {@code null}
     *         if the declaration line cannot be determined
     */
    protected static int[] annotationWindow(String source, CtType<?> type,
                                              String targetType, String targetName) {
        int declLine = -1;
        if ("method".equals(targetType) && targetName != null) {
            CtMethod<?> method = type.getMethods().stream()
                    .filter(m -> m.getSimpleName().equals(targetName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Method '" + targetName + "' not found in " + type.getQualifiedName()));
            declLine = method.getPosition().getLine();
        } else if ("field".equals(targetType) && targetName != null) {
            CtField<?> field = type.getFields().stream()
                    .filter(f -> f.getSimpleName().equals(targetName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Field '" + targetName + "' not found in " + type.getQualifiedName()));
            declLine = field.getPosition().getLine();
        } else if ("class".equals(targetType)) {
            declLine = type.getPosition().getLine();
        }
        String[] lines = source.split("\n", -1);
        int searchStartLine, searchEndLine;
        if (declLine > 0) {
            searchStartLine = annotationSearchStart(lines, declLine);
            searchEndLine = declLine;
        } else {
            searchStartLine = 1;
            searchEndLine = Integer.MAX_VALUE;
        }
        int startOffset = 0;
        for (int i = 0; i < searchStartLine - 1 && i < lines.length; i++) {
            startOffset += lines[i].length() + 1;
        }
        int endOffset = startOffset;
        for (int i = searchStartLine - 1; i < searchEndLine && i < lines.length; i++) {
            endOffset += lines[i].length() + 1;
        }
        endOffset = Math.min(endOffset, source.length());
        return new int[]{startOffset, endOffset};
    }

    // --- Shared code-safe text replacement (used by rename/move tools) ---

    /**
     * Replaces all word-boundary matches of {@code namePattern} with
     * {@code newName} in code regions of the source, skipping string literals,
     * char literals, and comments (line, block, Javadoc). This prevents
     * corrupting string content or comment text that happens to contain the
     * searched name.
     */
    protected static String replaceInCodeOnly(String source, java.util.regex.Pattern namePattern, String newName) {
        String[] lines = source.split("\n", -1);
        StringBuilder result = new StringBuilder();
        boolean inBlockComment = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (inBlockComment) {
                result.append(line);
                if (trimmed.contains("*/")) inBlockComment = false;
            } else if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
                    || trimmed.startsWith("/**") || trimmed.startsWith("*/")) {
                result.append(line);
                if (trimmed.startsWith("/*") && !trimmed.contains("*/")) inBlockComment = true;
            } else {
                result.append(replaceOutsideStrings(line, namePattern, newName));
                if (hasUnclosedBlockComment(line)) inBlockComment = true;
            }
            if (i < lines.length - 1) result.append("\n");
        }
        return result.toString();
    }

    /** Returns true if the line contains an unclosed block comment opener. */
    private static boolean hasUnclosedBlockComment(String line) {
        int idx = 0;
        boolean inString = false;
        boolean inChar = false;
        while (idx < line.length()) {
            char c = line.charAt(idx);
            if (inString) {
                if (c == '\\') { idx += 2; continue; }
                if (c == '"') inString = false;
                idx++; continue;
            }
            if (inChar) {
                if (c == '\\') { idx += 2; continue; }
                if (c == '\'') inChar = false;
                idx++; continue;
            }
            if (c == '"') { inString = true; idx++; continue; }
            if (c == '\'') { inChar = true; idx++; continue; }
            if (c == '/' && idx + 1 < line.length() && line.charAt(idx + 1) == '*') {
                int close = line.indexOf("*/", idx + 2);
                return close < 0;
            }
            idx++;
        }
        return false;
    }

    private static String replaceOutsideStrings(String line, java.util.regex.Pattern namePattern, String newName) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '"') {
                result.append(c);
                i++;
                while (i < line.length()) {
                    char sc = line.charAt(i);
                    result.append(sc);
                    i++;
                    if (sc == '\\' && i < line.length()) {
                        result.append(line.charAt(i));
                        i++;
                    } else if (sc == '"') break;
                }
            } else if (c == '\'') {
                result.append(c);
                i++;
                while (i < line.length()) {
                    char cc = line.charAt(i);
                    result.append(cc);
                    i++;
                    if (cc == '\\' && i < line.length()) {
                        result.append(line.charAt(i));
                        i++;
                    } else if (cc == '\'') break;
                }
            } else {
                int start = i;
                while (i < line.length()) {
                    char ch = line.charAt(i);
                    if (ch == '"' || ch == '\'') break;
                    if (ch == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') break;
                    if (ch == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*') break;
                    i++;
                }
                String codeSegment = line.substring(start, i);
                result.append(namePattern.matcher(codeSegment).replaceAll(newName));
                if (i < line.length() && line.charAt(i) == '/' && i + 1 < line.length()
                        && line.charAt(i + 1) == '/') {
                    result.append(line.substring(i));
                    i = line.length();
                } else if (i < line.length() && line.charAt(i) == '/' && i + 1 < line.length()
                        && line.charAt(i + 1) == '*') {
                    int end = line.indexOf("*/", i + 2);
                    if (end < 0) { result.append(line.substring(i)); i = line.length(); }
                    else { result.append(line, i, end + 2); i = end + 2; }
                }
            }
        }
        return result.toString();
    }

    // --- Fresh-parse helpers (P41: stale AST positions after edits) ---
    // The in-memory Spoon model's positions become stale after edits because
    // writeFile updates the file on disk but does not re-parse the model. Tools
    // that use position().getLine()/getEndLine() for insertion/removal points
    // would operate on wrong lines. These helpers re-parse the current on-disk
    // file in a lightweight noclasspath launcher to obtain accurate positions.

    /**
     * Re-parses the current on-disk file and returns the CtType matching the
     * given class name. Returns {@code null} if parsing fails or the type is
     * not found; callers fall back to the loaded model's type.
     */
    protected static CtType<?> locateFreshType(java.nio.file.Path file, String className) {
        try {
            spoon.Launcher fresh = new spoon.Launcher();
            fresh.addInputResource(file.toAbsolutePath().toString());
            fresh.getEnvironment().setNoClasspath(true);
            fresh.getEnvironment().setCommentEnabled(true);
            fresh.getEnvironment().setAutoImports(false);
            fresh.buildModel();
            return fresh.getModel().getAllTypes().stream()
                    .filter(t -> t.getQualifiedName().equals(className))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Re-parses the current on-disk file and returns the CtMethod matching
     * the given method name (and optional signature). Returns {@code null}
     * if not found. Shared by ReplaceMethodBodyTool and other tools that need
     * accurate method body positions after edits.
     */
    protected static CtMethod<?> locateFreshMethod(java.nio.file.Path file, String className,
                                                    String methodName, String signature) {
        CtType<?> t = locateFreshType(file, className);
        if (t == null) return null;
        java.util.List<CtMethod<?>> cands = t.getMethods().stream()
                .filter(m -> m.getSimpleName().equals(methodName))
                .collect(java.util.stream.Collectors.toList());
        if (signature == null || signature.isBlank()) {
            return cands.size() == 1 ? cands.get(0) : null;
        }
        List<String> sigParams = splitTopLevelCommas(signature);
        for (CtMethod<?> m : cands) {
            if (freshParamsMatch(m, sigParams)) return m;
        }
        return null;
    }

    /** Compares a method's parameter simple names against a signature list. */
    private static boolean freshParamsMatch(CtMethod<?> method, List<String> sigParams) {
        if (method.getParameters().size() != sigParams.size()) return false;
        for (int i = 0; i < sigParams.size(); i++) {
            String expected = eraseSigType(sigParams.get(i));
            String actual = method.getParameters().get(i).getType() != null
                    ? method.getParameters().get(i).getType().getSimpleName() : "";
            if (!actual.equals(expected)) return false;
        }
        return true;
    }

    private static String eraseSigType(String type) {
        String t = type.trim();
        int lt = t.indexOf('<');
        if (lt >= 0) t = t.substring(0, lt).trim();
        int lastDot = t.lastIndexOf('.');
        if (lastDot >= 0) t = t.substring(lastDot + 1);
        return t;
    }
}
