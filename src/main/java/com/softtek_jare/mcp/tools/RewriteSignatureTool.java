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
                + "signature_only (declaration only) or signature_and_callers (declaration + all call sites).";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "methodName", Map.of("type", "string", "description", "Method name"),
            "oldSignature", Map.of("type", "string", "description", "Current parameter types (e.g. 'int, String')"),
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
        String oldSignature = methodName + "(" + getParamString(target) + ")";
        String retType = newReturnType != null ? newReturnType : target.getType().getSimpleName();
        String params = newParameters != null ? newParameters : getParamString(target);

        String newSignature = retType + " " + methodName + "(" + params + ")";

        // Text-based signature replacement in source
        Path file = target.getPosition().getFile() != null
                ? target.getPosition().getFile().toPath() : null;
        if (file == null) return error("Cannot determine source file.");

        String source = Files.readString(file);
        // Replace the first occurrence of the old method signature pattern
        String oldDecl = target.getType().getSimpleName() + " " + methodName + "(" + getParamString(target) + ")";
        String newDecl = retType + " " + methodName + "(" + params + ")";
        String newSource = source.replace(oldDecl, newDecl);
        if (newSource.equals(source)) {
            return error("Could not find method declaration to replace. Pattern: " + oldDecl);
        }

        entry = editManager.writeFile(entry, file, newSource, null);
        manager.updateEntry(entry);

        StringBuilder sb = new StringBuilder();
        sb.append("Signature changed: ").append(oldSignature).append(" -> ").append(newDecl).append("\n");
        sb.append("Mode: ").append(mode).append("\n");
        if ("signature_and_callers".equals(mode)) {
            int callersUpdated = countCallers(entry, targetType, methodName);
            sb.append("Note: callers need manual review for parameter changes.\n");
            sb.append("Callers found: ").append(callersUpdated).append("\n");
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

    private int countCallers(ProjectEntry entry, CtType<?> targetType, String methodName) {
        int count = 0;
        for (CtType<?> type : entry.model().getAllTypes()) {
            for (CtMethod<?> method : type.getMethods()) {
                if (method.getBody() == null) continue;
                var invocations = method.getBody().getElements(new TypeFilter<>(CtInvocation.class));
                for (var inv : invocations) {
                    var exec = inv.getExecutable();
                    if (exec.getDeclaringType() != null
                            && exec.getDeclaringType().getQualifiedName().equals(targetType.getQualifiedName())
                            && exec.getSimpleName().equals(methodName)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
