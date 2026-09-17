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

/**
 * The {@code FindImplementationsTool} class.
 *
 * @author Alejandro Ferreira
 */
public class FindImplementationsTool extends BaseJavaTool {

/**
 * Constructs the {@code FindImplementationsTool} with the given project manager.
 */
    public FindImplementationsTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "find_implementations"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Finds all concrete classes that implement a given interface or extend a given abstract class."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "className", "string", "Fully qualified interface or abstract class name");
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "className"); }

/**
 * Handles the {@code find_implementations} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        var entry = findEntry(name);

        var targetType = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        if (!targetType.isInterface() && !targetType.isAbstract()) {
            return error("'" + className + "' is neither an interface nor an abstract class.");
        }

        List<String> implementations = new ArrayList<>();
        for (var type : entry.model().getAllTypes()) {
            if (type.equals(targetType)) continue;
            if (type.isInterface()) continue;
            if (implementsOrExtends(type, className, entry)) {
                implementations.add(type.getQualifiedName());
            }
        }

        implementations.sort(String::compareTo);

        StringBuilder sb = new StringBuilder();
        sb.append("# Implementations of `").append(className).append("`\n\n");

        if (implementations.isEmpty()) {
            sb.append("_No implementations found._");
        } else {
            sb.append("**Total:** ").append(implementations.size()).append("\n\n");
            for (String impl : implementations) {
                sb.append("- `").append(impl).append("`\n");
            }
        }

        return ok(sb);
    }

/**
 * Recursively checks whether a type implements or extends the target type.
 */
    private boolean implementsOrExtends(spoon.reflect.declaration.CtType<?> type, String targetFqn, com.softtek.mcp.model.ProjectEntry entry) {
        var superClass = type.getSuperclass();
        while (superClass != null) {
            if (targetFqn.equals(superClass.getQualifiedName())) return true;
            var superType = findType(entry, superClass.getQualifiedName());
            superClass = superType != null ? superType.getSuperclass() : null;
        }

        for (var iface : type.getSuperInterfaces()) {
            if (targetFqn.equals(iface.getQualifiedName())) return true;
            var ifaceType = findType(entry, iface.getQualifiedName());
            if (ifaceType != null && implementsOrExtends(ifaceType, targetFqn, entry)) return true;
        }

        return false;
    }

/**
 * Looks up a type by qualified name in the project model.
 */
    private spoon.reflect.declaration.CtType<?> findType(com.softtek.mcp.model.ProjectEntry entry, String qualifiedName) {
        return entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(qualifiedName))
                .findFirst()
                .orElse(null);
    }
}
