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

import spoon.reflect.declaration.CtAnnotationMethod;
import spoon.reflect.declaration.CtAnnotationType;

/**
 * The {@code GetAnnotationDetailsTool} class.
 *
 * @author Alejandro Ferreira
 */
public class GetAnnotationDetailsTool extends BaseJavaTool {

/**
 * Constructs the {@code GetAnnotationDetailsTool} with the given project manager.
 */
    public GetAnnotationDetailsTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "get_annotation_details"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Retrieves the definition of an annotation type including its attributes/elements."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "className", "string", "Fully qualified annotation class name");
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "className"); }

/**
 * Handles the {@code get_annotation_details} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        var entry = findEntry(name);

        var type = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Annotation type not found: " + className));

        if (!type.isAnnotationType()) {
            return domainError("DOMAIN_ERROR", "'" + className + "' is not an annotation type.");
        }

        CtAnnotationType<?> annType = (CtAnnotationType<?>) type;

        StringBuilder sb = new StringBuilder();
        sb.append("# Annotation: `@").append(className).append("`\n\n");

        var elements = annType.getMethods();
        if (elements.isEmpty()) {
            sb.append("_Marker annotation (no elements)._");
            return ok(sb);
        }

        sb.append("**Elements:** ").append(elements.size()).append("\n\n");
        for (var elem : elements) {
            String retType = elem.getType().toString();
            String defaultVal = "";
            if (elem instanceof CtAnnotationMethod<?> annMethod && annMethod.getDefaultExpression() != null) {
                defaultVal = " default " + annMethod.getDefaultExpression();
            }
            sb.append("- `").append(retType).append(" ").append(elem.getSimpleName())
              .append("()").append(defaultVal).append("`\n");
        }

        return ok(sb);
    }
}
