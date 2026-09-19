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
import com.softtek_jare.mcp.edit.TypeErasureChecker;
import com.softtek_jare.mcp.model.ProjectEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import spoon.reflect.declaration.CtType;

/**
 * The {@code AddMethodTool} — adds a new method to an existing class.
 *
 * @author Janusch Rentenatus
 */
public class AddMethodTool extends BaseJavaTool {

    private final EditManager editManager;

    public AddMethodTool(ProjectManager manager, EditManager editManager) {
        super(manager);
        this.editManager = editManager;
    }

    @Override protected String toolName() { return "add_method"; }
    @Override protected String toolDescription() {
        return "Add a new method to an existing class. Checks for erasure collisions. "
                + "Automatic import resolution applies to types in the body.";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "methodName", Map.of("type", "string", "description", "Method name"),
            "returnType", Map.of("type", "string", "description", "Return type (e.g. 'void', 'String')"),
            "parameters", Map.of("type", "string", "description", "Comma-separated 'type name' pairs (e.g. 'int count, String label')"),
            "modifiers", Map.of("type", "string", "description", "Optional: 'public', 'private', 'protected', 'static'"),
            "body", Map.of("type", "string", "description", "Optional: method body (without braces)")
        );
    }
    @Override protected List<String> toolRequired() { return req("name", "className", "methodName", "returnType"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String methodName = arg(request, "methodName");
        String returnType = arg(request, "returnType");
        String parameters = arg(request, "parameters");
        String modifiers = arg(request, "modifiers");
        String body = arg(request, "body");

        ProjectEntry entry = findEntry(name);
        requireEditable(entry);

        CtType<?> targetType = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        // Parse parameter types for erasure check. Use a bracket-aware split so
        // that generic parameter lists like "Map<K, V> map, int n" are not split
        // at the comma inside the angle brackets.
        List<String> paramTypes = new ArrayList<>();
        if (parameters != null && !parameters.isBlank()) {
            for (String p : splitTopLevelCommas(parameters)) {
                paramTypes.add(erasedParamSimpleName(p));
            }
        }

        // Erasure check
        var clash = new TypeErasureChecker().checkErasure(targetType, methodName, paramTypes);
        if (clash.clash()) {
            return error(clash.message());
        }

        // Auto-import resolution for return type, parameters, and body.
        // All three can reference types that need imports — not just the body.
        StringBuilder importCheckText = new StringBuilder();
        importCheckText.append(returnType);
        if (parameters != null && !parameters.isBlank()) {
            importCheckText.append(' ').append(parameters);
        }
        if (body != null && !body.isBlank()) {
            importCheckText.append(' ').append(body);
        }
        var importResult = new AutoImportResolver().resolve(entry, targetType, importCheckText.toString());
        if (!importResult.unresolvedTypes().isEmpty()) {
            return error("Cannot resolve type(s) in method signature or body: " + importResult.unresolvedTypes()
                    + ". Provide the fully qualified name or add the dependency.");
        }

        // Build method source string. Members live one indent level inside the
        // class body (4 spaces); the body is indented one level deeper than the
        // declaration, and the closing brace returns to the declaration indent.
        // The previous version left the body and closing brace at column 0.
        String memberIndent = "    ";
        String bodyIndent = "        ";
        StringBuilder methodSrc = new StringBuilder();
        methodSrc.append(memberIndent);
        if (modifiers != null && !modifiers.isBlank()) {
            methodSrc.append(modifiers).append(" ");
        }
        methodSrc.append(returnType).append(" ").append(methodName).append("(");
        if (parameters != null && !parameters.isBlank()) {
            methodSrc.append(parameters);
        }
        methodSrc.append(")");
        if (body != null && !body.isBlank()) {
            String[] bodyLines = body.stripIndent().split("\n", -1);
            methodSrc.append(" {\n");
            for (int i = 0; i < bodyLines.length; i++) {
                if (!bodyLines[i].isBlank()) methodSrc.append(bodyIndent);
                methodSrc.append(bodyLines[i]);
                if (i < bodyLines.length - 1) methodSrc.append("\n");
            }
            methodSrc.append("\n").append(memberIndent).append("}");
        } else {
            methodSrc.append(" {\n").append(memberIndent).append("}");
        }

        // Insert before last closing brace of the class
        Path file = targetType.getPosition().getFile() != null
                ? targetType.getPosition().getFile().toPath() : null;
        if (file == null) return error("Cannot determine source file.");

        String source = Files.readString(file);
        int lastBrace = findClassClosingBrace(source, targetType.getPosition().getEndLine());
        if (lastBrace < 0) return error("Malformed source: no closing brace found.");

        String newContent = source.substring(0, lastBrace)
                + methodSrc + "\n"
                + source.substring(lastBrace);
        newContent = insertImports(newContent, importResult.importsToAdd());

        entry = editManager.writeFile(entry, file, newContent, null);
        manager.updateEntry(entry);
        editManager.logEdit(toolName());

        StringBuilder sb = new StringBuilder();
        sb.append("Method added: ").append(className).append(".").append(methodName).append("\n");
        sb.append(formatMultiModuleWarning(entry));
        return ok(sb);
    }

    /**
     * Extracts the erased simple type name from a single parameter fragment
     * such as {@code "int count"}, {@code "final String label"}, or
     * {@code "Map<K, V> map"}. Strips a leading {@code final} modifier, drops
     * the parameter name (last whitespace-delimited token), erases generic
     * arguments, and reduces a fully-qualified name to its simple name.
     */
    private static String erasedParamSimpleName(String param) {
        String t = param.trim();
        if (t.isEmpty()) return t;
        while (t.startsWith("final ") || t.startsWith("final\t")) t = t.substring(6).trim();
        int sp = t.lastIndexOf(' ');
        String typePart = sp >= 0 ? t.substring(0, sp) : t;
        int lt = typePart.indexOf('<');
        if (lt >= 0) typePart = typePart.substring(0, lt).trim();
        int lastDot = typePart.lastIndexOf('.');
        if (lastDot >= 0) typePart = typePart.substring(lastDot + 1);
        return typePart;
    }
}
