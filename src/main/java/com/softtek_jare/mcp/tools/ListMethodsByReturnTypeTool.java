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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The {@code ListMethodsByReturnTypeTool} class.
 *
 * @author Alejandro Ferreira
 */
public class ListMethodsByReturnTypeTool extends BaseJavaTool {

/**
 * Constructs the {@code ListMethodsByReturnTypeTool} with the given project manager.
 */
    public ListMethodsByReturnTypeTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "list_methods_by_return_type"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Lists all methods in the project that return a specific type."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "returnType", "string", "Fully qualified or simple return type name");
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "returnType"); }

/**
 * Handles the {@code list_methods_by_return_type} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String returnType = arg(request, "returnType");
        var entry = findEntry(name);

        record MethodRef(String declaringClass, String methodName, String params, String retType) {}

        List<MethodRef> results = new ArrayList<>();

        for (var type : entry.model().getAllTypes().stream()
                .sorted(Comparator.comparing(t -> t.getQualifiedName())).toList()) {
            for (var method : type.getMethods()) {
                String ret = method.getType().toString();
                if (ret.equals(returnType) || ret.endsWith("." + returnType)) {
                    String params = method.getParameters().stream()
                            .map(p -> p.getType().toString())
                            .collect(Collectors.joining(", "));
                    results.add(new MethodRef(type.getQualifiedName(), method.getSimpleName(), params, ret));
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Methods returning `").append(returnType).append("`\n\n");
        sb.append("**Total:** ").append(results.size()).append(" methods\n\n");

        for (var r : results) {
            sb.append("- `").append(r.declaringClass).append(".").append(r.methodName)
              .append("(").append(r.params).append(")` → `").append(r.retType).append("`\n");
        }

        if (results.isEmpty()) {
            sb.append("_No methods found._");
        }

        return ok(sb);
    }
}
