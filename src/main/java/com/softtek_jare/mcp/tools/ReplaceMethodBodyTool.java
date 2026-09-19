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
import com.softtek_jare.mcp.edit.AutoImportResolver;
import com.softtek_jare.mcp.edit.EditManager;
import com.softtek_jare.mcp.model.ProjectEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

/**
 * The {@code ReplaceMethodBodyTool} — replaces the entire body of an existing method.
 *
 * @author Janusch Rentenatus
 */
public class ReplaceMethodBodyTool extends BaseJavaTool {

    private final EditManager editManager;
    private final AutoImportResolver importResolver;

    public ReplaceMethodBodyTool(ProjectManager manager, EditManager editManager) {
        super(manager);
        this.editManager = editManager;
        this.importResolver = new AutoImportResolver();
    }

    @Override protected String toolName() { return "replace_method_body"; }
    @Override protected String toolDescription() {
        return "Replace the entire body of an existing method. Signature must match exactly. "
                + "Automatic import resolution: types not resolvable in the current scope are searched "
                + "in the project and imports are inserted. Unresolved types block the edit.";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "methodName", Map.of("type", "string", "description", "Method name"),
            "signature", Map.of("type", "string", "description", "Comma-separated parameter types (e.g. 'int, String')"),
            "newBody", Map.of("type", "string", "description", "New method body (without enclosing braces)")
        );
    }
    @Override protected List<String> toolRequired() { return req("name", "className", "methodName", "newBody"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String methodName = arg(request, "methodName");
        String signature = arg(request, "signature");
        String newBody = arg(request, "newBody");

        ProjectEntry entry = findEntry(name);
        requireEditable(entry);

        CtType<?> targetType = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        // Find method by name and signature
        List<CtMethod<?>> candidates = targetType.getMethods().stream()
                .filter(m -> m.getSimpleName().equals(methodName))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return error("No method named '" + methodName + "' found in class " + targetType.getSimpleName());
        }

        CtMethod<?> target = null;
        if (signature != null && !signature.isBlank()) {
            List<String> sigParams = parseSignature(signature);
            for (CtMethod<?> m : candidates) {
                if (paramsMatch(m, sigParams)) {
                    target = m;
                    break;
                }
            }
            if (target == null) {
                return error("No method matching signature '" + methodName + "(" + signature + ")' found in class "
                        + targetType.getSimpleName() + ". Available: " + listAvailableSignatures(candidates));
            }
        } else {
            if (candidates.size() == 1) {
                target = candidates.get(0);
            } else {
                return error("Multiple methods named '" + methodName + "' found. Specify signature. Available: "
                        + listAvailableSignatures(candidates));
            }
        }

        // Auto-import resolution
        var importResult = importResolver.resolve(entry, targetType, newBody);
        if (!importResult.unresolvedTypes().isEmpty()) {
            return error("Cannot resolve type(s) in method body: " + importResult.unresolvedTypes()
                    + ". No matching import found in project or declared dependencies. "
                    + "Provide the fully qualified name or add the dependency.");
        }

        // Record body position before modifying AST
        if (target.getBody() == null) {
            return error("Method '" + methodName + "' has no body (abstract or interface method). "
                    + "Cannot replace body of a method without one.");
        }
        int bodyStartLine = target.getBody().getPosition().getLine();
        int bodyEndLine = target.getBody().getPosition().getEndLine();

        // Write file — text-based body replacement preserves formatting and comments
        Path file = targetType.getPosition().getFile() != null
                ? targetType.getPosition().getFile().toPath() : null;
        if (file == null) {
            return error("Cannot determine source file for class " + targetType.getSimpleName());
        }

        String source = LineEndings.readNormalized(file);
        String newContent = replaceBodyInSource(source, bodyStartLine, bodyEndLine, newBody);
        newContent = insertImports(newContent, importResult.importsToAdd());
        entry = editManager.writeFile(entry, file, newContent, null);
        manager.updateEntry(entry);
        editManager.logEdit(toolName());

        StringBuilder sb = new StringBuilder();
        sb.append("Method body replaced: ").append(className).append(".").append(methodName).append("\n");
        if (!importResult.importsToAdd().isEmpty()) {
            sb.append("Imports added: ").append(importResult.importsToAdd()).append("\n");
        }
        sb.append(formatMultiModuleWarning(entry));
        return ok(sb);
    }

    private List<String> parseSignature(String sig) {
        return splitTopLevelCommas(sig);
    }

    private boolean paramsMatch(CtMethod<?> method, List<String> sigParams) {
        if (method.getParameters().size() != sigParams.size()) return false;
        for (int i = 0; i < sigParams.size(); i++) {
            String expected = eraseType(sigParams.get(i));
            String actual = method.getParameters().get(i).getType() != null
                    ? method.getParameters().get(i).getType().getSimpleName() : "";
            if (!actual.equals(expected)) return false;
        }
        return true;
    }

    /**
     * Strips generic type arguments, array brackets, and fully-qualified
     * package prefixes from a user-provided parameter type so it can be
     * compared to the erased simple name from the AST. For example
     * {@code "List<String>"} becomes {@code "List"}, {@code "int[]"} stays
     * {@code "int[]"}, and {@code "java.lang.String"} becomes {@code "String"}.
     */
    private static String eraseType(String type) {
        String t = type.trim();
        int lt = t.indexOf('<');
        if (lt >= 0) t = t.substring(0, lt).trim();
        // Strip fully-qualified package prefix: "java.lang.String" -> "String"
        int lastDot = t.lastIndexOf('.');
        if (lastDot >= 0) t = t.substring(lastDot + 1);
        return t;
    }

    private String listAvailableSignatures(List<CtMethod<?>> methods) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < methods.size(); i++) {
            if (i > 0) sb.append(", ");
            CtMethod<?> m = methods.get(i);
            sb.append(m.getSimpleName()).append("(");
            for (int j = 0; j < m.getParameters().size(); j++) {
                if (j > 0) sb.append(", ");
                sb.append(m.getParameters().get(j).getType() != null
                        ? m.getParameters().get(j).getType().getSimpleName() : "?");
            }
            sb.append(")");
        }
        return sb.toString();
    }

    private static String replaceBodyInSource(String source, int bodyStartLine, int bodyEndLine, String newBody) {
        if (bodyStartLine < 1) return source;

        // The body position line is the line of the opening '{'. Find the first
        // '{' at or after that line, then brace-match to the closing '}'. Both
        // scans skip string/char literals and comments so braces inside them do
        // not corrupt the match. The previous line-based heuristic searched
        // backwards from the body line and matched the enclosing class brace,
        // deleting the method signature.
        int lineStart = lineStartOffset(source, bodyStartLine);
        int open = indexOfOpeningBrace(source, lineStart);
        if (open < 0) return source;
        int close = matchingBrace(source, open);
        if (close < 0) return source;

        // Indentation of the line containing the opening brace == method indent.
        int lineBegin = source.lastIndexOf('\n', open) + 1;
        String methodIndent = leadingWhitespace(source, lineBegin);
        String bodyIndent = methodIndent + "    ";

        String[] bodyLines = newBody.stripIndent().split("\n", -1);
        StringBuilder body = new StringBuilder();
        for (int j = 0; j < bodyLines.length; j++) {
            body.append(bodyIndent).append(bodyLines[j]);
            if (j < bodyLines.length - 1) body.append("\n");
        }

        return source.substring(0, open + 1)      // up to and including '{'
                + "\n" + body + "\n"
                + methodIndent + "}"              // closing brace at method indent
                + source.substring(close + 1);     // remainder of the file
    }

    /** Returns the char offset of the start of the given 1-indexed line. */
    private static int lineStartOffset(String source, int line1) {
        int idx = 0;
        for (int l = 1; l < line1; l++) {
            int nl = source.indexOf('\n', idx);
            if (nl < 0) return source.length();
            idx = nl + 1;
        }
        return idx;
    }

    /** Leading whitespace of the (sub)string starting at {@code from}. */
    private static String leadingWhitespace(String s, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t') sb.append(c); else break;
        }
        return sb.toString();
    }

    /** First '{' at or after {@code from}, skipping string/char literals and comments. */
    private static int indexOfOpeningBrace(String s, int from) {
        int i = from, n = s.length();
        boolean block = false;
        while (i < n) {
            char c = s.charAt(i);
            if (block) {
                if (c == '*' && i + 1 < n && s.charAt(i + 1) == '/') { block = false; i += 2; continue; }
                i++; continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                int nl = s.indexOf('\n', i); i = (nl < 0) ? n : nl + 1; continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') { block = true; i += 2; continue; }
            if (c == '"') { i = skipString(s, i); continue; }
            if (c == '\'') { i = skipChar(s, i); continue; }
            if (c == '{') return i;
            i++;
        }
        return -1;
    }

    /** Index of the '}' matching the '{' at {@code open}, or -1. */
    private static int matchingBrace(String s, int open) {
        int depth = 0, i = open, n = s.length();
        boolean block = false;
        while (i < n) {
            char c = s.charAt(i);
            if (block) {
                if (c == '*' && i + 1 < n && s.charAt(i + 1) == '/') { block = false; i += 2; continue; }
                i++; continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                int nl = s.indexOf('\n', i); i = (nl < 0) ? n : nl + 1; continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') { block = true; i += 2; continue; }
            if (c == '"') { i = skipString(s, i); continue; }
            if (c == '\'') { i = skipChar(s, i); continue; }
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return i; }
            i++;
        }
        return -1;
    }

    /** Returns the index just past the closing '"' of the string starting at {@code i}. */
    private static int skipString(String s, int i) {
        int n = s.length();
        i++; // opening quote
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\') { i += 2; continue; }
            if (c == '"') return i + 1;
            i++;
        }
        return n;
    }

    /** Returns the index just past the closing '\'' of the char literal starting at {@code i}. */
    private static int skipChar(String s, int i) {
        int n = s.length();
        i++; // opening quote
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\') { i += 2; continue; }
            if (c == '\'') return i + 1;
            i++;
        }
        return n;
    }
}
