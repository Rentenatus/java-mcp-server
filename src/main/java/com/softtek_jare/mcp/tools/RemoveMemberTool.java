/*
 * MIT License
 *
 * Copyright (c) 2026 Janusch Rentenatus
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

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek_jare.mcp.ProjectManager;
import com.softtek_jare.mcp.edit.EditManager;
import com.softtek_jare.mcp.model.ProjectEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * The {@code RemoveMemberTool} — removes a method, field, or class.
 *
 * @author Janusch Rentenatus
 */
public class RemoveMemberTool extends BaseJavaTool {

    private final EditManager editManager;

    public RemoveMemberTool(ProjectManager manager, EditManager editManager) {
        super(manager);
        this.editManager = editManager;
    }

    @Override protected String toolName() { return "remove_member"; }
    @Override protected String toolDescription() {
        return "Remove a method, field, or class. Safe mode (default): refuses if references exist. "
                + "Hard mode: removes regardless and lists dangling references.";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "memberName", Map.of("type", "string", "description", "Method or field name to remove"),
            "scope", Map.of("type", "string", "description", "'method' or 'field' (default: method)"),
            "mode", Map.of("type", "string", "description", "'safe' (default) or 'hard'")
        );
    }
    @Override protected List<String> toolRequired() { return req("name", "className", "memberName"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String memberName = arg(request, "memberName");
        String scope = arg(request, "scope");
        String mode = arg(request, "mode");
        if (scope == null) scope = "method";
        boolean hard = "hard".equals(mode);

        ProjectEntry entry = findEntry(name);
        requireEditable(entry);

        CtType<?> targetType = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        // Find references
        List<String> references = findReferences(entry, targetType, memberName, scope);

        if (!hard && !references.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Cannot remove '").append(memberName).append("' — ").append(references.size())
                    .append(" reference(s) found:\n");
            for (String ref : references) sb.append("  ").append(ref).append("\n");
            sb.append("Use mode='hard' to remove anyway (dangling references will break compilation).");
            return error(sb.toString());
        }

        // Remove the member via text manipulation
        Path file = targetType.getPosition().getFile() != null
                ? targetType.getPosition().getFile().toPath() : null;
        if (file == null) return error("Cannot determine source file.");

        String source = Files.readString(file);
        String newSource;
        if ("method".equals(scope)) {
            CtMethod<?> method = targetType.getMethods().stream()
                    .filter(m -> m.getSimpleName().equals(memberName))
                    .findFirst()
                    .orElse(null);
            if (method == null) {
                return error("Method '" + memberName + "' not found in " + className);
            }
            newSource = removeMethodByPosition(source, method);
        } else if ("field".equals(scope)) {
            CtField<?> field = targetType.getFields().stream()
                    .filter(f -> f.getSimpleName().equals(memberName))
                    .findFirst()
                    .orElse(null);
            if (field == null) {
                return error("Field '" + memberName + "' not found in " + className);
            }
            newSource = removeFieldByPosition(source, field);
        } else {
            return error("scope must be 'method' or 'field'");
        }
        if (newSource.equals(source)) {
            return error("Member '" + memberName + "' not found in source.");
        }

        entry = editManager.writeFile(entry, file, newSource, null);
        manager.updateEntry(entry);
        editManager.logEdit(toolName());

        StringBuilder sb = new StringBuilder();
        sb.append("Removed: ").append(className).append(".").append(memberName)
                .append(" (").append(hard ? "hard" : "safe").append(" mode)\n");
        if (hard && !references.isEmpty()) {
            sb.append("WARNING: Dangling references that will not compile:\n");
            for (String ref : references) sb.append("  ").append(ref).append("\n");
        }
        // Unresolved string-literal references
        List<String> stringRefs = scanStringLiterals(entry, memberName);
        if (!stringRefs.isEmpty()) {
            sb.append("Unresolved references (string literals):\n");
            for (String ref : stringRefs) sb.append("  ").append(ref).append("\n");
        }
        sb.append(formatMultiModuleWarning(entry));
        return ok(sb);
    }

    private List<String> findReferences(ProjectEntry entry, CtType<?> targetType, String memberName, String scope) {
        List<String> refs = new ArrayList<>();
        for (CtType<?> type : entry.model().getAllTypes()) {
            for (var method : type.getMethods()) {
                if (method.getBody() == null) continue;
                if ("method".equals(scope)) {
                    var invocations = method.getBody().getElements(new TypeFilter<>(CtInvocation.class));
                    for (var inv : invocations) {
                        var exec = inv.getExecutable();
                        if (exec.getDeclaringType() != null
                                && exec.getDeclaringType().getQualifiedName().equals(targetType.getQualifiedName())
                                && exec.getSimpleName().equals(memberName)) {
                            refs.add(type.getQualifiedName() + "." + method.getSimpleName() + " calls " + memberName);
                        }
                    }
                } else if ("field".equals(scope)) {
                    var accesses = method.getBody().getElements(new TypeFilter<>(CtFieldAccess.class));
                    for (var fa : accesses) {
                        var field = fa.getVariable();
                        if (field.getDeclaringType() != null
                                && field.getDeclaringType().getQualifiedName().equals(targetType.getQualifiedName())
                                && field.getSimpleName().equals(memberName)) {
                            refs.add(type.getQualifiedName() + "." + method.getSimpleName() + " accesses " + memberName);
                        }
                    }
                }
            }
        }
        return refs;
    }

    private String removeMethodByPosition(String source, CtMethod<?> method) {
        int startLine = method.getPosition().getLine();
        int endLine = method.getPosition().getEndLine();
        if (startLine < 1 || endLine < startLine) return source;
        String[] lines = source.split("\n", -1);
        int startIdx = startLine - 1; // 0-indexed

        // Walk backwards to include Javadoc/comments/annotations above the method
        int commentStart = startIdx;
        for (int i = startIdx - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) break;
            if (trimmed.startsWith("/**") || trimmed.startsWith("*") || trimmed.startsWith("*/")
                    || trimmed.startsWith("//") || trimmed.startsWith("/*")
                    || trimmed.startsWith("@")) {
                commentStart = i;
            } else {
                break;
            }
        }

        int endIdx = Math.min(endLine, lines.length); // exclusive
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i >= commentStart && i < endIdx) continue; // skip method + comments
            result.append(lines[i]);
            if (i < lines.length - 1) result.append("\n");
        }
        return result.toString();
    }

    private String removeFieldByPosition(String source, CtField<?> field) {
        int startLine = field.getPosition().getLine();
        int endLine = field.getPosition().getEndLine();
        if (startLine < 1) return source;
        String[] lines = source.split("\n", -1);
        int startIdx = startLine - 1;
        String fieldName = field.getSimpleName();

        // Multi-field declaration: "int x, y;" shares one line/endLine for both
        // fields. A position-based line removal would delete the sibling too.
        // When the declaration line contains a comma, remove only the named
        // fragment from that line and keep the rest.
        if (endLine <= startLine && lines[startIdx].contains(",")) {
            lines[startIdx] = removeFieldFragmentFromLine(lines[startIdx], fieldName);
            if (lines[startIdx].trim().isEmpty() || lines[startIdx].trim().equals(";")) {
                // Whole line reduced to nothing meaningful — drop it.
                return dropLines(lines, startIdx, startIdx + 1);
            }
            return String.join("\n", lines);
        }

        // Single-field declaration (possibly spanning multiple lines): remove
        // the declaration lines plus any Javadoc/comment block above them.

        // Walk backwards to include Javadoc/comments/annotations above the field
        int commentStart = startIdx;
        for (int i = startIdx - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) break;
            if (trimmed.startsWith("/**") || trimmed.startsWith("*") || trimmed.startsWith("*/")
                    || trimmed.startsWith("//") || trimmed.startsWith("/*")
                    || trimmed.startsWith("@")) {
                commentStart = i;
            } else {
                break;
            }
        }

        int endIdx = Math.min(Math.max(endLine, startLine), lines.length);
        return dropLines(lines, commentStart, endIdx);
    }

    /**
     * Removes a single named declarator from a multi-field declaration line such
     * as {@code "int x, y;"} or {@code "int x = 1, y = 2;"}, leaving the
     * remainder syntactically valid. The modifier/type prefix and trailing
     * semicolon are preserved. Only called when the line contains a comma.
     */
    private static String removeFieldFragmentFromLine(String line, String fieldName) {
        String quoted = java.util.regex.Pattern.quote(fieldName);
        // Non-first declarator: ", fieldName" or ", fieldName = init" (up to , or ;).
        String nonFirst = ",\\s*" + quoted + "\\s*(=\\s*[^,;]+)?";
        String updated = line.replaceAll(nonFirst, "");
        if (!updated.equals(line)) return updated;
        // First declarator: "fieldName, " or "fieldName = init, " — the type prefix
        // before fieldName is preserved because only the declarator onward is removed.
        String first = quoted + "\\s*(=\\s*[^,;]+)?\\s*,\\s*";
        return line.replaceAll(first, "");
    }

    /** Returns the source with the lines [start, end) removed. */
    private static String dropLines(String[] lines, int start, int end) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i >= start && i < end) continue;
            result.append(lines[i]);
            if (i < lines.length - 1) result.append("\n");
        }
        return result.toString();
    }

    private List<String> scanStringLiterals(ProjectEntry entry, String memberName) {
        List<String> result = new ArrayList<>();
        for (CtType<?> type : entry.model().getAllTypes()) {
            var literals = type.getElements(new TypeFilter<>(spoon.reflect.code.CtLiteral.class));
            for (var lit : literals) {
                if (lit.getValue() instanceof String s && s.contains(memberName)) {
                    result.add(type.getQualifiedName() + ":" + lit.getPosition().getLine()
                            + " — \"" + s + "\"");
                }
            }
        }
        return result;
    }
}
