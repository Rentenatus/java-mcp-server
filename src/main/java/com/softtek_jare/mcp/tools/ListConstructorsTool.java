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

package com.softtek_jare.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek_jare.mcp.ProjectManager;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The {@code ListConstructorsTool} class.
 *
 * @author Alejandro Ferreira
 */
public class ListConstructorsTool extends BaseJavaTool {

/**
 * Constructs the {@code ListConstructorsTool} with the given project manager.
 */
    public ListConstructorsTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "list_constructors"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Lists all constructors in a class with their parameters and bodies."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "className", "string", "Fully qualified class name");
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "className"); }

/**
 * Handles the {@code list_constructors} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        var entry = findEntry(name);

        var type = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + className));

        if (!type.isClass()) {
            return domainError("DOMAIN_ERROR", "'" + className + "' is not a class (no constructors).");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Constructors in `").append(className).append("`\n\n");

        var ctClass = (spoon.reflect.declaration.CtClass<?>) type;
        var constructors = ctClass.getConstructors();
        if (constructors.isEmpty()) {
            sb.append("_No explicit constructors (default constructor)._");
            return ok(sb);
        }

        for (var ctor : constructors) {
            String params = ctor.getParameters().stream()
                    .map(p -> p.getType().toString() + " " + p.getSimpleName())
                    .collect(Collectors.joining(", "));
            String vis = ctor.isPublic() ? "public" : ctor.isProtected() ? "protected" : ctor.isPrivate() ? "private" : "package-private";

            sb.append("## `").append(vis).append(" ").append(className).append("(").append(params).append(")`\n\n");

            var annotations = ctor.getAnnotations();
            if (!annotations.isEmpty()) {
                sb.append("Annotations:\n");
                for (var ann : annotations) {
                    sb.append("- `@").append(ann.getAnnotationType().getQualifiedName()).append("`\n");
                }
                sb.append("\n");
            }

            if (ctor.getBody() != null) {
                String body = ctor.getBody().prettyprint();
                sb.append("```java\n").append(body).append("\n```\n\n");
            }
        }

        return ok(sb);
    }
}
