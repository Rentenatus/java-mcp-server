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
import com.softtek_jare.mcp.edit.LineEndings;

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
                + "Hard mode: removes regardless and lists dangling references. "
                + "For overloaded methods, provide the optional 'signature' parameter "
                + "(comma-separated parameter types, e.g. 'int, String') to disambiguate.";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "memberName", Map.of("type", "string", "description", "Method or field name to remove"),
            "scope", Map.of("type", "string", "description", "'method' or 'field' (default: method)"),
            "mode", Map.of("type", "string", "description", "'safe' (default) or 'hard'"),
            "signature", Map.of("type", "string", "description", "Comma-separated parameter types (e.g. 'int, String') to disambiguate overloaded methods")
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
        String signature = arg(request, "signature");
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
            return domainError("DOMAIN_ERROR", sb.toString());
        }

        // Remove the member via text manipulation
        Path file = targetType.getPosition().getFile() != null
                ? targetType.getPosition().getFile().toPath() : null;
        if (file == null) return domainError("DOMAIN_ERROR", "Cannot determine source file.");

        String source = LineEndings.readNormalized(file);
        String raw;
        try {
            raw = java.nio.file.Files.readString(file);
        } catch (java.io.IOException e) {
            raw = source;
        }
        // P45: use fresh-parse positions to avoid stale line numbers after prior edits
        CtType<?> freshType = locateFreshType(file, className);
        CtType<?> posType = (freshType != null) ? freshType : targetType;
        String newSource;
        if ("method".equals(scope)) {
            // P54: disambiguate overloaded methods via signature, or error
            java.util.List<CtMethod<?>> matching = posType.getMethods().stream()
                    .filter(m -> m.getSimpleName().equals(memberName))
                    .collect(java.util.stream.Collectors.toList());
            if (matching.isEmpty()) {
                return domainError("DOMAIN_ERROR", "Method '" + memberName + "' not found in " + className);
            }
            CtMethod<?> method;
            if (signature != null && !signature.isBlank()) {
                List<String> sigParams = splitTopLevelCommas(signature);
                method = matching.stream()
                        .filter(m -> paramsMatch(m, sigParams))
                        .findFirst().orElse(null);
                if (method == null) {
                    return domainError("DOMAIN_ERROR", "No method matching signature '" + memberName + "(" + signature
                            + ")' found in " + className);
                }
            } else if (matching.size() == 1) {
                method = matching.get(0);
            } else {
                return domainError("DOMAIN_ERROR", "Multiple methods named '" + memberName + "' in " + className
                        + ". Provide the 'signature' parameter to disambiguate.");
            }
            newSource = removeMethodByPosition(source, raw, method);
        } else if ("field".equals(scope)) {
            CtField<?> field = posType.getFields().stream()
                    .filter(f -> f.getSimpleName().equals(memberName))
                    .findFirst()
                    .orElse(null);
            if (field == null) {
                return domainError("DOMAIN_ERROR", "Field '" + memberName + "' not found in " + className);
            }
            newSource = removeFieldByPosition(source, raw, field);
        } else {
            return domainError("DOMAIN_ERROR", "scope must be 'method' or 'field'");
        }
        if (newSource.equals(source)) {
            return domainError("DOMAIN_ERROR", "Member '" + memberName + "' not found in source.");
        }

        entry = editManager.writeFile(entry, file, newSource, null);
        manager.updateEntry(entry);
        editManager.logEdit(toolName() + ": " + scope + " " + memberName + " in " + className);

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

    private String removeMethodByPosition(String source, String raw, CtMethod<?> method) {
        var pos = method.getPosition();
        int diskStart = pos.getSourceStart();
        int diskEnd = pos.getSourceEnd();
        if (diskStart >= 0 && diskEnd >= diskStart) {
            int mStart = mapDiskOffsetToNormalized(raw, source, diskStart);
            int mEnd = mapDiskOffsetToNormalized(raw, source, diskEnd);
            if (mStart >= 0 && mEnd >= mStart && mEnd < source.length() && source.charAt(mEnd) == '}') {
                // Preceding standalone annotation/comment lines: keep the existing
                // line-based heuristic, but only remove whole lines strictly before
                // the method's start line (so a method that shares a line with
                // other content is not deleted together with that content).
                String[] lines = source.split("\n", -1);
                int startIdx = pos.getLine() - 1;
                int lineBegin = source.lastIndexOf('\n', mStart) + 1;
                int removeStart = mStart;
                if (startIdx >= 0 && startIdx < lines.length) {
                    int commentLineStart = commentBlockStart(lines, startIdx);
                    if (commentLineStart < startIdx) {
                        int cs = lineStartOffset(source, commentLineStart + 1);
                        if (cs >= 0 && cs <= mStart) removeStart = cs;
                    } else if (source.substring(lineBegin, mStart).strip().isEmpty()) {
                        // No preceding comment/annotation block and the method
                        // starts at the beginning of its line: drop the leading
                        // indentation too so the following line does not inherit it.
                        removeStart = lineBegin;
                    }
                }
                int removeEnd = mEnd + 1;
                // Consume the trailing newline right after the method's closing
                // brace to avoid leaving a blank line — but only when the next
                // char is actually a newline (the method may share its line with
                // following content).
                if (removeEnd < source.length() && source.charAt(removeEnd) == '\n') {
                    removeEnd++;
                }
                return source.substring(0, removeStart) + source.substring(removeEnd);
            }
        }
        // Fallback: line-based removal (no reliable source offsets).
        return removeMethodByLines(source, method);
    }

    /** Line-based fallback for method removal. */
    private String removeMethodByLines(String source, CtMethod<?> method) {
        int startLine = method.getPosition().getLine();
        int endLine = method.getPosition().getEndLine();
        if (startLine < 1 || endLine < startLine) return source;
        String[] lines = source.split("\n", -1);
        int startIdx = startLine - 1; // 0-indexed

        int commentStart = commentBlockStart(lines, startIdx);

        int endIdx = Math.min(endLine, lines.length); // exclusive
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i >= commentStart && i < endIdx) continue; // skip method + comments
            result.append(lines[i]);
            if (i < lines.length - 1) result.append("\n");
        }
        return result.toString();
    }

    private String removeFieldByPosition(String source, String raw, CtField<?> field) {
        var pos = field.getPosition();
        int startLine = pos.getLine();
        if (startLine < 1) return source;
        String[] lines = source.split("\n", -1);
        int startIdx = startLine - 1;
        String fieldName = field.getSimpleName();

        // Multi-field declaration: "int x, y;" shares one line/endLine for both
        // fields. A position-based line removal would delete the sibling too.
        // When the declaration line contains a comma, remove only the named
        // fragment from that line and keep the rest.
        int endLine = pos.getEndLine();
        if (endLine <= startLine && startIdx < lines.length && lines[startIdx].contains(",")) {
            lines[startIdx] = removeFieldFragmentFromLine(lines[startIdx], fieldName);
            if (lines[startIdx].trim().isEmpty() || lines[startIdx].trim().equals(";")) {
                // Whole line reduced to nothing meaningful — drop it.
                return dropLines(lines, startIdx, startIdx + 1);
            }
            return String.join("\n", lines);
        }

        // Prefer exact char-span removal so a field that shares a line with
        // other content (e.g. a single-line class with several fields) does not
        // pull its siblings into the deletion.
        int diskStart = pos.getSourceStart();
        int diskEnd = pos.getSourceEnd();
        if (diskStart >= 0 && diskEnd >= diskStart) {
            int fStart = mapDiskOffsetToNormalized(raw, source, diskStart);
            int fEnd = mapDiskOffsetToNormalized(raw, source, diskEnd);
            if (fStart >= 0 && fEnd >= fStart && fEnd < source.length()) {
                int lineBegin = source.lastIndexOf('\n', fStart) + 1;
                int commentLineStart = commentBlockStart(lines, startIdx);
                int removeStart = fStart;
                if (commentLineStart < startIdx) {
                    int cs = lineStartOffset(source, commentLineStart + 1);
                    if (cs >= 0 && cs <= fStart) removeStart = cs;
                } else if (source.substring(lineBegin, fStart).strip().isEmpty()) {
                    removeStart = lineBegin;
                }
                int removeEnd = fEnd + 1;
                if (removeEnd < source.length() && source.charAt(removeEnd) == '\n') removeEnd++;
                return source.substring(0, removeStart) + source.substring(removeEnd);
            }
        }

        // Fallback: line-based removal (possibly multi-line declaration).
        int commentStart = commentBlockStart(lines, startIdx);
        int endIdx = Math.min(Math.max(endLine, startLine), lines.length);
        return dropLines(lines, commentStart, endIdx);
    }

    /**
     * Removes a single named declarator from a multi-field declaration line such
     * as {@code "int x, y;"}, {@code "int x = 1, y = 2;"}, or
     * {@code "String s = foo(1, 2), t = "a,b";"}, leaving the remainder
     * syntactically valid. The modifier/type prefix and trailing semicolon are
     * preserved. Uses a string/char/paren-aware comma scanner so that commas
     * inside string literals, char literals, and method-call arguments are not
     * mistaken for declarator separators.
     *
     * @param line      the full source line containing the multi-field declaration
     * @param fieldName  the declarator name to remove
     * @return the line with the named declarator removed, or the original line
     *         if the field name was not found
     */
    private static String removeFieldFragmentFromLine(String line, String fieldName) {
        // Find top-level comma positions (not inside strings, chars, or parentheses)
        List<Integer> commas = new ArrayList<>();
        int depth = 0;
        boolean inString = false, inChar = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inString) {
                if (c == '\\') { i++; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (inChar) {
                if (c == '\\') { i++; continue; }
                if (c == '\'') inChar = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '\'') { inChar = true; continue; }
            if (c == '(') { depth++; continue; }
            if (c == ')') { if (depth > 0) depth--; continue; }
            if (c == ',' && depth == 0) commas.add(i);
        }

        // Build segments: [0, comma1), [comma1+1, comma2), ..., [lastComma+1, end]
        List<int[]> segments = new ArrayList<>();
        int segStart = 0;
        for (int comma : commas) {
            segments.add(new int[]{segStart, comma});
            segStart = comma + 1;
        }
        // Last segment goes to end of line
        segments.add(new int[]{segStart, line.length()});

        // Find the segment containing the field name as a whole word.
        // The first segment includes the type prefix; subsequent segments are
        // pure declarators.
        int removeIdx = -1;
        for (int si = 0; si < segments.size(); si++) {
            int s = segments.get(si)[0];
            int e = segments.get(si)[1];
            String segText = line.substring(s, e);
            if (containsWholeWord(segText, fieldName)) {
                removeIdx = si;
                break;
            }
        }
        if (removeIdx < 0) return line; // not found

        // Remove the segment. For the first segment, keep the type prefix
        // (everything before the field name) and discard the declarator.
        // For subsequent segments, discard the entire segment + its leading comma.
        StringBuilder result = new StringBuilder();
        for (int si = 0; si < segments.size(); si++) {
            if (si == removeIdx) {
                if (si == 0) {
                    // First segment: preserve type prefix before the field name.
                    String segText = line.substring(segments.get(si)[0], segments.get(si)[1]);
                    int namePos = findWholeWord(segText, fieldName);
                    if (namePos >= 0) {
                        result.append(segText.substring(0, namePos).stripTrailing());
                    }
                }
                // Skip the removed segment (and its trailing/leading comma)
                continue;
            }
            if (si > 0 && result.length() > 0 && result.charAt(result.length() - 1) != ',') {
                // Re-insert the comma separator between remaining segments
                // (unless the previous segment was removed and already handled)
                if (si - 1 != removeIdx) {
                    // Previous segment was kept — comma already part of segment text? No.
                    // We need to add the comma that was at the boundary.
                }
            }
            // Actually, simpler: append segments with commas between them,
            // skipping the removed one.
            if (si > 0) result.append(",");
            result.append(line.substring(segments.get(si)[0], segments.get(si)[1]));
        }
        return result.toString();
    }

    /** Returns true if {@code text} contains {@code word} as a whole word. */
    private static boolean containsWholeWord(String text, String word) {
        return findWholeWord(text, word) >= 0;
    }

    /** Returns the char index of {@code word} as a whole word in {@code text}, or -1. */
    private static int findWholeWord(String text, String word) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\\b" + java.util.regex.Pattern.quote(word) + "\\b");
        var m = p.matcher(text);
        return m.find() ? m.start() : -1;
    }

    /** Compares a method's parameter types against a signature list (P54). */
    private static boolean paramsMatch(CtMethod<?> method, List<String> sigParams) {
        if (method.getParameters().size() != sigParams.size()) return false;
        for (int i = 0; i < sigParams.size(); i++) {
            String expected = eraseType(sigParams.get(i));
            String actual = method.getParameters().get(i).getType() != null
                    ? method.getParameters().get(i).getType().getSimpleName() : "";
            if (!actual.equals(expected)) return false;
        }
        return true;
    }

    /** Strips generics and package prefix from a parameter type string. Varargs ("...") are normalized to "[]". */
    private static String eraseType(String type) {
        String t = type.trim();
        // Normalize varargs: "int..." -> "int[]" (Spoon stores varargs as array)
        if (t.endsWith("...")) {
            t = t.substring(0, t.length() - 3) + "[]";
        }
        int lt = t.indexOf('<');
        if (lt >= 0) t = t.substring(0, lt).trim();
        int lastDot = t.lastIndexOf('.');
        if (lastDot >= 0) t = t.substring(lastDot + 1);
        return t;
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

    /**
     * Walks backwards from {@code startIdx - 1} over contiguous Javadoc, comment,
     * and annotation lines to find the first line of the block preceding the
     * member declaration. Returns {@code startIdx} if there is no preceding block.
     */
    private static int commentBlockStart(String[] lines, int startIdx) {
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
        return commentStart;
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
