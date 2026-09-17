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
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * The {@code RenameSymbolTool} — renames a method, class, or field across
 * every caller, backed by the type-resolved AST.
 *
 * @author Janusch Rentenatus
 */
public class RenameSymbolTool extends BaseJavaTool {

    private final EditManager editManager;

    public RenameSymbolTool(ProjectManager manager, EditManager editManager) {
        super(manager);
        this.editManager = editManager;
    }

    @Override protected String toolName() { return "rename_symbol"; }
    @Override protected String toolDescription() {
        return "Rename a class, method, or field across every caller. Backed by type-resolved "
                + "find_references. Returns unresolved_references for string literals matching "
                + "the old name (reflection boundary).";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name of the target"),
            "oldName", Map.of("type", "string", "description", "Current name of the symbol"),
            "newName", Map.of("type", "string", "description", "New name for the symbol"),
            "scope", Map.of("type", "string", "description", "Optional: 'method', 'field', or 'class' (default: method)")
        );
    }
    @Override protected List<String> toolRequired() { return req("name", "className", "oldName", "newName"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String oldName = arg(request, "oldName");
        String newName = arg(request, "newName");
        String scope = arg(request, "scope");
        if (scope == null) scope = "method";

        ProjectEntry entry = findEntry(name);
        requireEditable(entry);

        // Find the target type
        CtType<?> targetType = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        int callersUpdated = 0;
        List<Path> affectedFiles = new ArrayList<>();

        if ("method".equals(scope)) {
            callersUpdated = renameMethod(entry, targetType, oldName, newName, affectedFiles);
        } else if ("field".equals(scope)) {
            callersUpdated = renameField(entry, targetType, oldName, newName, affectedFiles);
        } else if ("class".equals(scope)) {
            callersUpdated = renameClass(entry, targetType, oldName, newName, affectedFiles);
        } else {
            return error("scope must be 'method', 'field', or 'class'");
        }

        // Write affected files via EditManager — text-based replacement preserves formatting
        for (Path file : affectedFiles) {
            String source = Files.readString(file);
            String newContent = source.replaceAll("\\b" + java.util.regex.Pattern.quote(oldName) + "\\b", newName);
            if (!newContent.equals(source)) {
                entry = editManager.writeFile(entry, file, newContent, null);
                manager.updateEntry(entry);
            }
        }

        // For class renames, rename the source file to match the new class name
        if ("class".equals(scope)) {
            Path oldFile = targetType.getPosition().getFile() != null
                    ? targetType.getPosition().getFile().toPath() : null;
            if (oldFile != null) {
                Path newFile = oldFile.resolveSibling(newName + ".java");
                if (!oldFile.equals(newFile) && Files.exists(oldFile)) {
                    Files.move(oldFile, newFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    entry = editManager.writeFile(entry, newFile, Files.readString(newFile), null);
                    manager.updateEntry(entry);
                }
            }
        }

        // Scan for unresolved string-literal references
        List<UnresolvedRef> unresolved = scanUnresolvedReferences(entry, oldName);

        StringBuilder sb = new StringBuilder();
        sb.append("Renamed: ").append(oldName).append(" -> ").append(newName).append("\n");
        sb.append("Callers updated: ").append(callersUpdated).append("\n");
        sb.append("Files changed: ").append(affectedFiles.size()).append("\n");
        if (!unresolved.isEmpty()) {
            sb.append("Unresolved references (string literals):\n");
            for (UnresolvedRef ref : unresolved) {
                sb.append("  ").append(ref.file).append(":").append(ref.line)
                        .append(" — ").append(ref.context).append("\n");
            }
        }
        sb.append(formatMultiModuleWarning(entry));
        return ok(sb);
    }

    private int renameMethod(ProjectEntry entry, CtType<?> targetType, String oldName,
                             String newName, List<Path> affectedFiles) {
        // Rename the declaration
        int count = 0;
        for (CtMethod<?> method : targetType.getMethods()) {
            if (method.getSimpleName().equals(oldName)) {
                method.setSimpleName(newName);
                Path file = method.getPosition().getFile() != null
                        ? method.getPosition().getFile().toPath() : null;
                if (file != null && !affectedFiles.contains(file)) {
                    affectedFiles.add(file);
                }
                count++;
            }
        }

        // Rename all invocations across all loaded types
        for (CtType<?> type : entry.model().getAllTypes()) {
            for (CtMethod<?> method : type.getMethods()) {
                if (method.getBody() == null) continue;
                List<CtInvocation<?>> invocations = method.getBody()
                        .getElements(new TypeFilter<>(CtInvocation.class));
                for (CtInvocation<?> inv : invocations) {
                    var exec = inv.getExecutable();
                    if (exec.getDeclaringType() != null
                            && exec.getDeclaringType().getQualifiedName().equals(targetType.getQualifiedName())
                            && exec.getSimpleName().equals(oldName)) {
                        exec.setSimpleName(newName);
                        Path file = type.getPosition().getFile() != null
                                ? type.getPosition().getFile().toPath() : null;
                        if (file != null && !affectedFiles.contains(file)) {
                            affectedFiles.add(file);
                        }
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int renameField(ProjectEntry entry, CtType<?> targetType, String oldName,
                            String newName, List<Path> affectedFiles) {
        int count = 0;
        // Rename the declaration
        for (var field : targetType.getFields()) {
            if (field.getSimpleName().equals(oldName)) {
                field.setSimpleName(newName);
                Path file = field.getPosition().getFile() != null
                        ? field.getPosition().getFile().toPath() : null;
                if (file != null && !affectedFiles.contains(file)) {
                    affectedFiles.add(file);
                }
                count++;
            }
        }
        // Rename all field accesses
        for (CtType<?> type : entry.model().getAllTypes()) {
            for (CtMethod<?> method : type.getMethods()) {
                if (method.getBody() == null) continue;
                var accesses = method.getBody().getElements(
                        new TypeFilter<>(spoon.reflect.code.CtFieldAccess.class));
                for (var fa : accesses) {
                    var fieldRef = fa.getVariable();
                    if (fieldRef.getDeclaringType() != null
                            && fieldRef.getDeclaringType().getQualifiedName().equals(targetType.getQualifiedName())
                            && fieldRef.getSimpleName().equals(oldName)) {
                        fieldRef.setSimpleName(newName);
                        Path file = type.getPosition().getFile() != null
                                ? type.getPosition().getFile().toPath() : null;
                        if (file != null && !affectedFiles.contains(file)) {
                            affectedFiles.add(file);
                        }
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int renameClass(ProjectEntry entry, CtType<?> targetType, String oldName,
                            String newName, List<Path> affectedFiles) {
        targetType.setSimpleName(newName);
        Path file = targetType.getPosition().getFile() != null
                ? targetType.getPosition().getFile().toPath() : null;
        if (file != null) affectedFiles.add(file);

        int count = 1;
        // Update all type references
        for (CtType<?> type : entry.model().getAllTypes()) {
            var refs = type.getElements(new TypeFilter<>(spoon.reflect.reference.CtTypeReference.class));
            for (var ref : refs) {
                if (ref.getQualifiedName() != null
                        && ref.getQualifiedName().equals(targetType.getQualifiedName())) {
                    ref.setSimpleName(newName);
                    Path f = type.getPosition().getFile() != null
                            ? type.getPosition().getFile().toPath() : null;
                    if (f != null && !affectedFiles.contains(f)) {
                        affectedFiles.add(f);
                    }
                    count++;
                }
            }
        }
        return count;
    }

    private List<UnresolvedRef> scanUnresolvedReferences(ProjectEntry entry, String oldName) {
        List<UnresolvedRef> result = new ArrayList<>();
        for (CtType<?> type : entry.model().getAllTypes()) {
            var literals = type.getElements(new TypeFilter<CtLiteral<String>>(CtLiteral.class));
            for (CtLiteral<?> lit : literals) {
                if (lit.getValue() instanceof String s && s.contains(oldName)) {
                    Path file = lit.getPosition().getFile() != null
                            ? lit.getPosition().getFile().toPath() : null;
                    int line = lit.getPosition().getLine();
                    String context = lit.getPosition().getCompilationUnit() != null
                            ? "\"" + s + "\"" : "\"" + s + "\"";
                    result.add(new UnresolvedRef(
                            file != null ? file.getFileName().toString() : "unknown",
                            line, context));
                }
            }
        }
        return result;
    }

    private record UnresolvedRef(String file, int line, String context) {}
}
