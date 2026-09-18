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

import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * The {@code RewriteSignatureTool} — changes a method's signature (parameters, return type).
 *
 * @author Janusch Rentenatus
 */
public class RewriteSignatureTool extends BaseJavaTool {

    private final EditManager editManager;

    public RewriteSignatureTool(ProjectManager manager, EditManager editManager) {
        super(manager);
        this.editManager = editManager;
    }

    @Override protected String toolName() { return "rewrite_signature"; }
    @Override protected String toolDescription() {
        return "Add, remove, or rename parameters; change return type. Two modes: "
                + "signature_only (rename just the declaration, no caller updates — useful for "
                + "interface methods or staged refactoring) or signature_and_callers (default: "
                + "declaration + TODO markers at all call sites for manual review).";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "methodName", Map.of("type", "string", "description", "Method name"),
            "newReturnType", Map.of("type", "string", "description", "New return type (omit to keep current)"),
            "newParameters", Map.of("type", "string", "description", "New parameter list (e.g. 'int count, String label')"),
            "mode", Map.of("type", "string", "description", "'signature_only' or 'signature_and_callers' (default)")
        );
    }
    @Override protected List<String> toolRequired() { return req("name", "className", "methodName"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String methodName = arg(request, "methodName");
        String newReturnType = arg(request, "newReturnType");
        String newParameters = arg(request, "newParameters");
        String mode = arg(request, "mode");
        if (mode == null) mode = "signature_and_callers";

        ProjectEntry entry = findEntry(name);
        requireEditable(entry);

        CtType<?> targetType = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        // Find the method
        List<CtMethod<?>> candidates = new ArrayList<>();
        for (CtMethod<?> m : targetType.getMethods()) {
            if (m.getSimpleName().equals(methodName)) candidates.add(m);
        }
        if (candidates.isEmpty()) {
            return error("Method '" + methodName + "' not found in " + targetType.getSimpleName());
        }
        if (candidates.size() > 1) {
            return error("Multiple methods named '" + methodName + "'. Not yet supported for overloaded methods.");
        }
        CtMethod<?> target = candidates.get(0);

        // Build new signature string
        String retType = newReturnType != null ? newReturnType : target.getType().getSimpleName();
        String params = newParameters != null ? newParameters : getParamString(target);

        // Text-based signature replacement in source
        Path file = target.getPosition().getFile() != null
                ? target.getPosition().getFile().toPath() : null;
        if (file == null) return error("Cannot determine source file.");

        String source = Files.readString(file);
        // Find the method declaration by locating the declaration line from the
        // AST and matching the signature there. The previous approach built an
        // exact string from erased simple names (strips generics like
        // List<String>) and used replaceFirst on the whole file, which could
        // match the same pattern inside a comment. We now scope the search to
        // the declaration line(s) and use a token-based match tolerant of
        // generic brackets. Returns null when the declaration could not be
        // found at all; the returned source may be unchanged when the
        // signature already matches (e.g. user re-applies generics that
        // Spoon erased to a simple name).
        int declLine = target.getPosition().getLine();
        String oldDecl = target.getType().getSimpleName() + " " + methodName + "(" + getParamString(target) + ")";
        String newDecl = retType + " " + methodName + "(" + params + ")";
        String newSource = replaceSignatureOnLine(source, methodName, oldDecl, newDecl, declLine);
        if (newSource == null) {
            return error("Could not find method declaration to replace. Pattern: " + oldDecl);
        }

        if (!newSource.equals(source)) {
            entry = editManager.writeFile(entry, file, newSource, null);
            manager.updateEntry(entry);
            editManager.logEdit(toolName());
        }

        int callersUpdated = 0;
        if ("signature_and_callers".equals(mode)) {
            // Update caller sites: rewrite argument lists at invocation points
            callersUpdated = updateCallers(entry, targetType, methodName, target);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Signature changed: ").append(methodName).append("(").append(getParamString(target)).append(") -> ").append(newDecl).append("\n");
        sb.append("Mode: ").append(mode).append("\n");
        if ("signature_and_callers".equals(mode)) {
            sb.append("Callers updated: ").append(callersUpdated).append("\n");
        }
        sb.append(formatMultiModuleWarning(entry));
        return ok(sb);
    }

    private String getParamString(CtMethod<?> method) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < method.getParameters().size(); i++) {
            if (i > 0) sb.append(", ");
            var p = method.getParameters().get(i);
            sb.append(p.getType() != null ? p.getType().getSimpleName() : "?").append(" ").append(p.getSimpleName());
        }
        return sb.toString();
    }

    /**
     * Replaces the method signature on the declaration line(s). Tries an exact
     * string match first (fast path). If that fails — e.g. because the source
     * uses generic types like {@code List<String>} while the AST simple name is
     * erased to {@code List} — falls back to locating the method name token on
     * the declaration line, finding the matching ')' for the parameter list,
     * and replacing from the return type start through ')' with newDecl.
     */
    private static String replaceSignatureOnLine(String source, String methodName,
                                                  String oldDecl, String newDecl, int declLine) {
        String[] lines = source.split("\n", -1);
        if (declLine < 1 || declLine > lines.length) return null;
        int start = declLine - 1; // 0-indexed
        // Consider up to 3 lines for the declaration (multi-line signatures).
        int end = Math.min(start + 3, lines.length);

        // Fast path: exact match within the window.
        for (int i = start; i < end; i++) {
            if (lines[i].contains(oldDecl)) {
                lines[i] = lines[i].replace(oldDecl, newDecl);
                return String.join("\n", lines);
            }
        }

        // Fallback: the source uses generics in the return type (e.g.
        // List<String>) that were erased to a simple name (List) in oldDecl,
        // so the fast path cannot match. Instead, locate the method name token
        // on the declaration line, find the matching ')' for the parameter
        // list, and replace from the start of the return type through ')' with
        // newDecl. This avoids matching inside comments.
        for (int i = start; i < end; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue;
            int nameIdx = findMethodNameToken(lines[i], methodName);
            if (nameIdx < 0) continue;
            int parenStart = lines[i].indexOf('(', nameIdx);
            if (parenStart < 0) continue;
            int parenEnd = findMatchingParen(lines[i], parenStart);
            if (parenEnd < 0) continue;
            int retStart = findReturnTypeStart(lines[i], nameIdx);
            if (retStart < 0) continue;
            lines[i] = lines[i].substring(0, retStart) + newDecl + lines[i].substring(parenEnd + 1);
            return String.join("\n", lines);
        }
        return null;
    }

    /**
     * Finds the start index of {@code methodName} when it appears as a whole
     * identifier token (not a substring of a longer name) followed by optional
     * whitespace and {@code '('}. Returns -1 if not found.
     */
    private static int findMethodNameToken(String line, String methodName) {
        int idx = 0;
        while (true) {
            idx = line.indexOf(methodName, idx);
            if (idx < 0) return -1;
            if (idx > 0 && Character.isJavaIdentifierPart(line.charAt(idx - 1))) { idx++; continue; }
            int j = idx + methodName.length();
            while (j < line.length() && Character.isWhitespace(line.charAt(j))) j++;
            if (j < line.length() && line.charAt(j) == '(') return idx;
            idx++;
        }
    }

    /**
     * Scans backwards from {@code nameIdx} (the method name position) to find
     * the start of the return type on the same line. Handles generic return
     * types like {@code List<String>} or {@code Map<K, V>} by tracking angle
     * bracket depth. Returns the index of the first character of the return
     * type, or -1 if none is found.
     */
    private static int findReturnTypeStart(String line, int nameIdx) {
        int i = nameIdx - 1;
        while (i >= 0 && Character.isWhitespace(line.charAt(i))) i--;
        if (i < 0) return -1;
        int depth = 0;
        for (; i >= 0; i--) {
            char c = line.charAt(i);
            if (c == '>') { depth++; continue; }
            if (c == '<') { depth--; if (depth < 0) { i++; break; } continue; }
            if (depth > 0) continue; // inside generics: any char is part of the type
            if (Character.isJavaIdentifierPart(c) || c == '.' || c == '[' || c == ']') continue;
            i++; // boundary (space, brace, etc. at depth 0)
            break;
        }
        if (i < 0) i = 0;
        return i;
    }

    /** Returns the index of the ')' matching the '(' at {@code open}, or -1. */
    private static int findMatchingParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private int updateCallers(ProjectEntry entry, CtType<?> targetType, String methodName, CtMethod<?> target) {
        // Collect all files containing calls to the method
        java.util.Set<java.nio.file.Path> callerFiles = new java.util.HashSet<>();
        for (CtType<?> type : entry.model().getAllTypes()) {
            for (CtMethod<?> method : type.getMethods()) {
                if (method.getBody() == null) continue;
                var invocations = method.getBody().getElements(new TypeFilter<>(CtInvocation.class));
                for (var inv : invocations) {
                    var exec = inv.getExecutable();
                    if (exec.getDeclaringType() != null
                            && exec.getDeclaringType().getQualifiedName().equals(targetType.getQualifiedName())
                            && exec.getSimpleName().equals(methodName)) {
                        java.nio.file.Path f = type.getPosition().getFile() != null
                                ? type.getPosition().getFile().toPath() : null;
                        if (f != null) callerFiles.add(f);
                    }
                }
            }
        }
        // Insert TODO comments at call sites — text-based search avoids stale AST positions
        int count = 0;
        java.nio.file.Path declaringFile = target.getPosition().getFile() != null
                ? target.getPosition().getFile().toPath() : null;
        for (java.nio.file.Path file : callerFiles) {
            // Skip the declaring file: the signature was already rewritten above,
            // and inserting a TODO before the declaration line would corrupt it.
            if (declaringFile != null && file.equals(declaringFile)) continue;
            try {
                String source = java.nio.file.Files.readString(file);
                String[] lines = source.split("\n", -1);
                StringBuilder newSource = new StringBuilder();
                int localCount = 0;
                for (int i = 0; i < lines.length; i++) {
                    // Search for methodName( in code (not in comments or strings)
                    String trimmed = lines[i].trim();
                    boolean isComment = trimmed.startsWith("//") || trimmed.startsWith("*")
                            || trimmed.startsWith("/*") || trimmed.startsWith("/**");
                    if (!isComment && lines[i].contains(methodName + "(")) {
                        newSource.append("// TODO: signature of ").append(methodName)
                                .append(" changed — review arguments\n");
                        localCount++;
                    }
                    newSource.append(lines[i]);
                    if (i < lines.length - 1) newSource.append("\n");
                }
                if (localCount > 0) {
                    entry = editManager.writeFile(entry, file, newSource.toString(), null);
                    manager.updateEntry(entry);
                    editManager.logEdit(toolName());
                }
                count += localCount;
            } catch (java.io.IOException e) {
                // skip unreadable files
            }
        }
        return count;
    }
}
