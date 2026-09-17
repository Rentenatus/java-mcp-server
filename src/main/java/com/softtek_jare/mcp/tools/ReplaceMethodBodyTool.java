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
        int bodyStartLine = target.getBody() != null ? target.getBody().getPosition().getLine() : -1;
        int bodyEndLine = target.getBody() != null ? target.getBody().getPosition().getEndLine() : -1;

        // Write file — text-based body replacement preserves formatting and comments
        Path file = targetType.getPosition().getFile() != null
                ? targetType.getPosition().getFile().toPath() : null;
        if (file == null) {
            return error("Cannot determine source file for class " + targetType.getSimpleName());
        }

        String source = Files.readString(file);
        String newContent = replaceBodyInSource(source, bodyStartLine, bodyEndLine, newBody);
        entry = editManager.writeFile(entry, file, newContent, null);
        manager.updateEntry(entry);

        StringBuilder sb = new StringBuilder();
        sb.append("Method body replaced: ").append(className).append(".").append(methodName).append("\n");
        if (!importResult.importsToAdd().isEmpty()) {
            sb.append("Imports added: ").append(importResult.importsToAdd()).append("\n");
        }
        sb.append(formatMultiModuleWarning(entry));
        return ok(sb);
    }

    private List<String> parseSignature(String sig) {
        List<String> params = new ArrayList<>();
        for (String p : sig.split(",")) {
            params.add(p.trim());
        }
        return params;
    }

    private boolean paramsMatch(CtMethod<?> method, List<String> sigParams) {
        if (method.getParameters().size() != sigParams.size()) return false;
        for (int i = 0; i < sigParams.size(); i++) {
            String expected = sigParams.get(i);
            String actual = method.getParameters().get(i).getType() != null
                    ? method.getParameters().get(i).getType().getSimpleName() : "";
            if (!actual.equals(expected)) return false;
        }
        return true;
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
        if (bodyStartLine < 1 || bodyEndLine < bodyStartLine) return source;
        String[] lines = source.split("\n", -1);
        int startIdx = bodyStartLine - 1; // 0-indexed
        int endIdx = Math.min(bodyEndLine, lines.length); // exclusive

        // Find the opening brace line and closing brace line
        int openBraceIdx = -1;
        for (int i = startIdx - 1; i >= 0 && i < lines.length; i--) {
            if (lines[i].contains("{")) { openBraceIdx = i; break; }
        }
        if (openBraceIdx < 0) openBraceIdx = startIdx; // fallback

        int closeBraceIdx = -1;
        for (int i = endIdx; i < lines.length; i++) {
            if (lines[i].trim().equals("}") || lines[i].trim().startsWith("}")) { closeBraceIdx = i; break; }
        }
        if (closeBraceIdx < 0) closeBraceIdx = endIdx - 1; // fallback

        // Detect indentation from the opening brace line
        String indent = "";
        for (char c : lines[openBraceIdx].toCharArray()) {
            if (c == ' ') indent += " ";
            else break;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == openBraceIdx) {
                result.append(lines[i]); // keep the opening brace line
            } else if (i > openBraceIdx && i <= closeBraceIdx) {
                if (i == closeBraceIdx) {
                    result.append(indent).append(newBody.stripIndent()).append("\n");
                    result.append(lines[i]); // keep the closing brace line
                }
                // skip old body lines
            } else {
                result.append(lines[i]);
            }
            if (i < lines.length - 1) result.append("\n");
        }
        return result.toString();
    }
}
