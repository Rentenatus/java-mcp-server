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

package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.LombokMemberPredictor;
import com.softtek.mcp.ProjectManager;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The {@code InspectClassTool} class.
 *
 * @author Alejandro Ferreira
 */
public class InspectClassTool extends BaseJavaTool {

/**
 * Constructs the {@code InspectClassTool} with the given project manager.
 */
    public InspectClassTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "inspect_class"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Retrieves detailed information about a class: fields, methods, superclass, interfaces, and annotations. When the project was loaded with delombok=true, Lombok-generated members are present in the AST. Otherwise, a 'Lombok-predicted' section is appended listing what Lombok would generate."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "className", "string", "Fully qualified class name",
                "withJavadoc", "boolean", "If true (default), includes the Javadoc comment in the output");
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "className"); }

/**
 * Handles the {@code inspect_class} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        Boolean wj = (Boolean) request.arguments().get("withJavadoc");
        boolean withJavadoc = (wj == null) || wj;
        var entry = findEntry(name);

        var type = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + className));

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(type.getQualifiedName()).append("\n\n");

        if (entry.delomboked()) {
            sb.append("> ✅ **Lombok delombok applied** (version ")
                    .append(entry.lombokVersion() == null ? "?" : entry.lombokVersion())
                    .append("). Lombok-generated members are visible below.\n\n");
        } else if (LombokMemberPredictor.hasLombokAnnotations(type)) {
            sb.append("> ⚠️ **Lombok detected but not delomboked.** Members that Lombok would generate are listed in the `## Lombok-predicted members` section below. Re-load the project with `delombok: true` to see them as real AST members.\n\n");
        }

        if (withJavadoc) {
            String doc = type.getDocComment();
            if (doc != null && !doc.isBlank()) {
                sb.append("## Javadoc\n\n");
                sb.append("```\n");
                sb.append(doc.stripIndent().strip());
                sb.append("\n```\n\n");
            }
        }
        String kind;
        if (type.isClass()) kind = "**CLASS**";
        else if (type.isInterface()) kind = "**INTERFACE**";
        else if (type.isEnum()) kind = "**ENUM**";
        else if (type.isAnnotationType()) kind = "**ANNOTATION**";
        else kind = "**TYPE**";
        sb.append("| Property | Value |\n");
        sb.append("|----------|-------|\n");
        sb.append("| **Kind** | ").append(kind).append(" |\n");

        String visibility = type.isPublic() ? "public" : type.isProtected() ? "protected" : type.isPrivate() ? "private" : "package-private";
        sb.append("| **Visibility** | `").append(visibility).append("` |\n");
        if (type.isAbstract()) sb.append("| **Abstract** | ✅ |\n");
        if (type.isFinal()) sb.append("| **Final** | ✅ |\n");
        if (type.isStatic()) sb.append("| **Static** | ✅ |\n");

        if (type.getSuperclass() != null) {
            sb.append("| **Superclass** | `").append(type.getSuperclass().getQualifiedName()).append("` |\n");
        }
        var interfaces = type.getSuperInterfaces();
        if (!interfaces.isEmpty()) {
            String ifaces = interfaces.stream()
                    .map(i -> "`" + i.getQualifiedName() + "`")
                    .collect(Collectors.joining(", "));
            sb.append("| **Interfaces** | ").append(ifaces).append(" |\n");
        }

        sb.append("\n## Fields (").append(type.getFields().size()).append(")\n\n");
        for (var field : type.getFields()) {
            String fVis = field.isPublic() ? "public" : field.isProtected() ? "protected" : field.isPrivate() ? "private" : "";
            sb.append("- `").append(fVis).append(" ").append(field.getType()).append(" ").append(field.getSimpleName()).append("`");
            if (field.isStatic()) sb.append(" `static`");
            if (field.isFinal()) sb.append(" `final`");
            sb.append("\n");
        }

        sb.append("\n## Methods (").append(type.getMethods().size()).append(")\n\n");
        for (var method : type.getMethods()) {
            String params = method.getParameters().stream()
                    .map(p -> p.getType().toString() + " " + p.getSimpleName())
                    .collect(Collectors.joining(", "));
            String mVis = method.isPublic() ? "public" : method.isProtected() ? "protected" : method.isPrivate() ? "private" : "";
            sb.append("- `").append(mVis).append(" ").append(method.getType()).append(" ").append(method.getSimpleName())
              .append("(").append(params).append(")`");
            if (method.isStatic()) sb.append(" `static`");
            if (method.isAbstract()) sb.append(" `abstract`");
            sb.append("\n");
        }

        var annotations = type.getAnnotations();
        if (!annotations.isEmpty()) {
            sb.append("\n## Annotations\n\n");
            for (var ann : annotations) {
                sb.append("- `@").append(ann.getAnnotationType().getQualifiedName()).append("`\n");
            }
        }

        if (!entry.delomboked() && LombokMemberPredictor.hasLombokAnnotations(type)) {
            var predicted = LombokMemberPredictor.predict(type);
            if (!predicted.isEmpty()) {
                sb.append("\n## Lombok-predicted members (").append(predicted.size()).append(")\n\n");
                sb.append("_These members are NOT in the AST. They would be generated by Lombok at compile time._\n\n");
                for (var m : predicted.stream()
                        .sorted(Comparator.comparing(LombokMemberPredictor.PredictedMember::signature))
                        .toList()) {
                    sb.append("- `[").append(m.kind()).append("]` `").append(m.signature()).append("`\n");
                }
            }
        }

        return ok(sb);
    }
}
