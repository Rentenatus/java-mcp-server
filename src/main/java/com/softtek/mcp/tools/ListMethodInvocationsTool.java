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

import java.util.*;
import java.util.stream.Collectors;

import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * The {@code ListMethodInvocationsTool} class.
 *
 * @author Alejandro Ferreira
 */
public class ListMethodInvocationsTool extends BaseJavaTool {

/**
 * Constructs the {@code ListMethodInvocationsTool} with the given project manager.
 */
    public ListMethodInvocationsTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "list_method_invocations"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Lists all method calls made within a given method's body."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "methodName", Map.of("type", "string", "description", "Method name")
        );
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "className", "methodName"); }

/**
 * Handles the {@code list_method_invocations} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String methodName = arg(request, "methodName");
        var entry = findEntry(name);

        var type = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + className));

        var method = type.getMethods().stream()
                .filter(m -> m.getSimpleName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Method '" + methodName + "' not found in " + className));

        StringBuilder sb = new StringBuilder();
        sb.append("# Method Invocations in `").append(className).append(".").append(methodName).append("`\n\n");

        if (method.getBody() == null) {
            sb.append("_No method body (abstract or native)._");
            return ok(sb);
        }

        List<CtInvocation<?>> invocations = method.getBody().getElements(new TypeFilter<>(CtInvocation.class));

        if (invocations.isEmpty()) {
            sb.append("_No method invocations found._");
            return ok(sb);
        }

        sb.append("**Total calls:** ").append(invocations.size()).append("\n\n");

        Map<String, Integer> callCounts = new LinkedHashMap<>();
        for (var inv : invocations) {
            var exec = inv.getExecutable();
            String targetType = exec.getDeclaringType() != null
                    ? exec.getDeclaringType().getQualifiedName()
                    : "(unknown)";
            String callKey = targetType + "." + exec.getSimpleName() + "(...)";
            callCounts.merge(callKey, 1, Integer::sum);
        }

        for (var e : callCounts.entrySet()) {
            sb.append("- `").append(e.getKey()).append("` (x").append(e.getValue()).append(")\n");
        }

        return ok(sb);
    }
}
