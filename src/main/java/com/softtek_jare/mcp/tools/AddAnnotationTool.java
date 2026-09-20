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
import java.util.Map;

import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

/**
 * The {@code AddAnnotationTool} — adds an annotation to a class, method, or field.
 *
 * @author Janusch Rentenatus
 */
public class AddAnnotationTool extends BaseJavaTool {

    private final EditManager editManager;

    public AddAnnotationTool(ProjectManager manager, EditManager editManager) {
        super(manager);
        this.editManager = editManager;
    }

    @Override protected String toolName() { return "add_annotation"; }
    @Override protected String toolDescription() {
        return "Add an annotation to a class, method, or field. Checks for duplicates.";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "targetType", Map.of("type", "string", "description", "'class', 'method', or 'field'"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "targetName", Map.of("type", "string", "description", "Method or field name (omit for class)"),
            "annotation", Map.of("type", "string", "description", "Annotation name (simple or qualified)"),
            "attributes", Map.of("type", "string", "description", "Optional: key=value pairs (e.g. 'value=/new')")
        );
    }
    @Override protected java.util.List<String> toolRequired() {
        return req("name", "targetType", "className", "annotation");
    }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception {
        String name = arg(request, "name");
        String targetType = arg(request, "targetType");
        String className = arg(request, "className");
        String targetName = arg(request, "targetName");
        String annotation = arg(request, "annotation");
        String attributes = arg(request, "attributes");

        ProjectEntry entry = findEntry(name);
        requireEditable(entry);

        CtType<?> type = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        String annotationSrc = "@" + annotation;
        if (attributes != null && !attributes.isBlank()) {
            annotationSrc += "(" + attributes + ")";
        }

        Path file = type.getPosition().getFile() != null
                ? type.getPosition().getFile().toPath() : null;
        if (file == null) return domainError("NO_SOURCE_FILE",
                "Cannot determine source file for class '" + className + "'.",
                ctx("className", className, "annotation", annotation));

        String source = LineEndings.readNormalized(file);
        String[] lines = source.split("\n", -1);
        int insertLine; // 0-indexed line before which to insert the annotation

        // P44: use fresh-parse positions to avoid stale declaration lines after prior edits
        CtType<?> freshType = locateFreshType(file, className);
        CtType<?> posType = (freshType != null) ? freshType : type;

        if ("class".equals(targetType)) {
            // Duplicate check
            boolean alreadyHas = type.getAnnotations().stream()
                    .anyMatch(a -> annotationMatches(a.getAnnotationType().getQualifiedName(), annotation));
            if (alreadyHas) return domainError("DUPLICATE_ANNOTATION",
                    "Class '" + className + "' already has annotation @" + annotation + ".",
                    ctx("className", className, "targetType", "class", "annotation", annotation,
                        "suggestion", "Use edit_annotation to modify the existing annotation or remove_annotation first."));
            int declLine = posType.getPosition().getLine();
            if (declLine < 1) return domainError("NO_DECL_LINE",
                    "Cannot determine class declaration line for '" + className + "'.",
                    ctx("className", className, "annotation", annotation));
            insertLine = declLine - 1; // 0-indexed declaration line; annotation inserted right before it
        } else if ("method".equals(targetType)) {
            if (targetName == null) return domainError("MISSING_TARGET_NAME",
                    "targetName required for method annotations.",
                    ctx("className", className, "targetType", "method", "annotation", annotation,
                        "suggestion", "Provide the 'targetName' parameter with the method name."));
            CtMethod<?> method = type.getMethods().stream()
                    .filter(m -> m.getSimpleName().equals(targetName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Method '" + targetName + "' not found in " + className));
            // Duplicate check
            boolean alreadyHas = method.getAnnotations().stream()
                    .anyMatch(a -> annotationMatches(a.getAnnotationType().getQualifiedName(), annotation));
            if (alreadyHas) return domainError("DUPLICATE_ANNOTATION",
                    "Method '" + targetName + "' already has annotation @" + annotation + ".",
                    ctx("className", className, "targetType", "method", "targetName", targetName,
                        "annotation", annotation,
                        "suggestion", "Use edit_annotation to modify or remove_annotation first."));
            // P50: error on overloaded methods
            long methodCount = type.getMethods().stream()
                    .filter(m -> m.getSimpleName().equals(targetName)).count();
            if (methodCount > 1) {
                return domainError("OVERLOADED_METHOD",
                        "Multiple methods named '" + targetName + "' in " + className
                        + ". Annotation tools do not yet support overloaded methods.",
                        ctx("className", className, "targetName", targetName, "annotation", annotation,
                            "suggestion", "Rename the overloaded methods to disambiguate, or edit the source manually."));
            }
            // Validate @Override semantics: reject when the method does not
            // override any supertype method (would cause a compile error).
            String overrideErr = validateOverride(method, annotation, targetName);
            if (overrideErr != null) return domainError("OVERRIDE_INVALID", overrideErr,
                    ctx("className", className, "targetName", targetName, "annotation", "Override",
                        "suggestion", "Ensure the method overrides a supertype method, or remove @Override."));
            // P44: use fresh positions for insertion line
            CtMethod<?> freshMethod = posType.getMethods().stream()
                    .filter(m -> m.getSimpleName().equals(targetName))
                    .findFirst().orElse(null);
            int declLine = (freshMethod != null) ? freshMethod.getPosition().getLine() : method.getPosition().getLine();
            if (declLine < 1) return domainError("NO_DECL_LINE",
                    "Cannot determine method declaration line for '" + targetName + "' in '" + className + "'.",
                    ctx("className", className, "targetType", "method", "targetName", targetName, "annotation", annotation));
            insertLine = declLine - 1; // 0-indexed declaration line; annotation inserted right before it
        } else if ("field".equals(targetType)) {
            if (targetName == null) return domainError("MISSING_TARGET_NAME",
                    "targetName required for field annotations.",
                    ctx("className", className, "targetType", "field", "annotation", annotation,
                        "suggestion", "Provide the 'targetName' parameter with the field name."));
            CtField<?> field = type.getFields().stream()
                    .filter(f -> f.getSimpleName().equals(targetName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Field '" + targetName + "' not found in " + className));
            // Duplicate check
            boolean alreadyHas = field.getAnnotations().stream()
                    .anyMatch(a -> annotationMatches(a.getAnnotationType().getQualifiedName(), annotation));
            if (alreadyHas) return domainError("DUPLICATE_ANNOTATION",
                    "Field '" + targetName + "' already has annotation @" + annotation + ".",
                    ctx("className", className, "targetType", "field", "targetName", targetName,
                        "annotation", annotation,
                        "suggestion", "Use edit_annotation to modify or remove_annotation first."));
            // P44: use fresh positions for insertion line
            CtField<?> freshField = posType.getFields().stream()
                    .filter(f -> f.getSimpleName().equals(targetName))
                    .findFirst().orElse(null);
            int declLine = (freshField != null) ? freshField.getPosition().getLine() : field.getPosition().getLine();
            if (declLine < 1) return domainError("NO_DECL_LINE",
                    "Cannot determine field declaration line for '" + targetName + "' in '" + className + "'.",
                    ctx("className", className, "targetType", "field", "targetName", targetName, "annotation", annotation));
            insertLine = declLine - 1; // 0-indexed declaration line; annotation inserted right before it
        } else {
            return domainError("INVALID_TARGET_TYPE",
                    "targetType must be 'class', 'method', or 'field'",
                    ctx("targetType", targetType, "validTypes", "class, method, field"));
        }

        // Insert annotation line before the declaration
        String indent = "";
        for (char c : lines[insertLine].toCharArray()) {
            if (c == ' ' || c == '\t') indent += c;
            else break;
        }
        String[] newLines = new String[lines.length + 1];
        System.arraycopy(lines, 0, newLines, 0, insertLine);
        newLines[insertLine] = indent + annotationSrc;
        System.arraycopy(lines, insertLine, newLines, insertLine + 1, lines.length - insertLine);
        String newSource = String.join("\n", newLines);

        entry = editManager.writeFile(entry, file, newSource, null);
        manager.updateEntry(entry);
        editManager.logEdit(toolName() + ": @" + annotation + " on " + targetType + (targetName != null ? " " + targetName : "") + " in " + className);

        StringBuilder sb = new StringBuilder();
        sb.append("Annotation added: @").append(annotation).append(" on ").append(targetType);
        if (targetName != null) sb.append(" ").append(targetName);
        sb.append("\n");
        sb.append(formatMultiModuleWarning(entry));
        return ok(sb);
    }

    private static boolean annotationMatches(String qualifiedName, String search) {
        if (qualifiedName.equals(search)) return true;
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 && qualifiedName.substring(dot + 1).equals(search);
    }

    /**
     * Validates that {@code @Override} is only added to a method that genuinely
     * overrides a supertype method. Returns an error message if the annotation
     * is {@code @Override} (or {@code java.lang.Override}) and the method has no
     * top-level definition in any supertype; returns {@code null} otherwise
     * (including for non-{@code @Override} annotations).
     *
     * <p>In noclasspath mode, supertypes may not resolve, so this only rejects
     * when {@code getTopDefinitions()} is confidently empty — i.e. the method
     * truly has no override target. For external supertypes that did not
     * resolve, {@code getTopDefinitions()} is also empty, but this is the same
     * risk the compiler itself runs in noclasspath parsing; the alternative
     * (silently accepting a bogus @Override) produces a guaranteed compile
     * error, which is worse.
     */
    private static String validateOverride(CtMethod<?> method, String annotation, String targetName) {
        String simple = annotation;
        int dot = annotation.lastIndexOf('.');
        if (dot >= 0) simple = annotation.substring(dot + 1);
        if (!"Override".equals(simple)) return null;
        if (method.getTopDefinitions().isEmpty()) {
            return "Method '" + targetName + "' does not override any supertype method. "
                    + "@Override would cause a compile error.";
        }
        return null;
    }
}
