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

import com.softtek.mcp.ProjectManager;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The {@code InspectMethodTool} class.
 *
 * @author Alejandro Ferreira
 */
public class InspectMethodTool extends BaseJavaTool {

/**
 * Constructs the {@code InspectMethodTool} with the given project manager.
 */
    public InspectMethodTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "inspect_method"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Retrieves detailed information about a specific method: signature, parameters, return type, annotations, and source body."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "methodName", Map.of("type", "string", "description", "Method name"),
            "withJavadoc", Map.of("type", "boolean", "description", "If true (default), includes the Javadoc comment in the output")
        );
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "className", "methodName"); }

/**
 * Handles the {@code inspect_method} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String methodName = arg(request, "methodName");
        Boolean wj = (Boolean) request.arguments().get("withJavadoc");
        boolean withJavadoc = (wj == null) || wj;
        var entry = findEntry(name);

        var type = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + className));

        var methods = type.getMethods().stream()
                .filter(m -> m.getSimpleName().equals(methodName))
                .toList();

        if (methods.isEmpty()) {
            throw new IllegalArgumentException("Method '" + methodName + "' not found in " + className);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Method: `").append(methodName).append("` in `").append(className).append("`\n\n");

        for (var method : methods) {
            String params = method.getParameters().stream()
                    .map(p -> p.getType().toString() + " " + p.getSimpleName())
                    .collect(Collectors.joining(", "));
            String retType = method.getType().toString();

            sb.append("## Signature\n\n");
            sb.append("```java\n");
            if (method.isPublic()) sb.append("public ");
            else if (method.isProtected()) sb.append("protected ");
            else if (method.isPrivate()) sb.append("private ");
            if (method.isStatic()) sb.append("static ");
            if (method.isAbstract()) sb.append("abstract ");
            sb.append(retType).append(" ").append(methodName).append("(").append(params).append(")");
            sb.append("\n```\n\n");

            if (withJavadoc) {
                String doc = method.getDocComment();
                if (doc != null && !doc.isBlank()) {
                    sb.append("## Javadoc\n\n");
                    sb.append("```\n");
                    sb.append(doc.stripIndent().strip());
                    sb.append("\n```\n\n");
                }
            }

            sb.append("| Property | Value |\n");
            sb.append("|----------|-------|\n");
            sb.append("| **Return type** | `").append(retType).append("` |\n");
            sb.append("| **Parameters** | ").append(method.getParameters().size()).append(" |\n");
            sb.append("| **Visibility** | ").append(
                method.isPublic() ? "public" :
                method.isProtected() ? "protected" :
                method.isPrivate() ? "private" : "package-private").append(" |\n");

            if (method.isStatic()) sb.append("| **Static** | ✅ |\n");
            if (method.isAbstract()) sb.append("| **Abstract** | ✅ |\n");
            if (method.isFinal()) sb.append("| **Final** | ✅ |\n");

            var annotations = method.getAnnotations();
            if (!annotations.isEmpty()) {
                sb.append("\n## Annotations\n\n");
                for (var ann : annotations) {
                    sb.append("- `@").append(ann.getAnnotationType().getQualifiedName()).append("`\n");
                }
            }

            if (method.getBody() != null) {
                sb.append("\n## Body\n\n```java\n");
                sb.append(method.getBody().prettyprint());
                sb.append("\n```\n");
            } else {
                sb.append("\n_No method body (abstract or native)._");
            }
        }

        return ok(sb);
    }
}
