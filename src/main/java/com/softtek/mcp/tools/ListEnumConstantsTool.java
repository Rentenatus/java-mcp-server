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

import spoon.reflect.declaration.CtEnum;

/**
 * The {@code ListEnumConstantsTool} class.
 *
 * @author Alejandro Ferreira
 */
public class ListEnumConstantsTool extends BaseJavaTool {

/**
 * Constructs the {@code ListEnumConstantsTool} with the given project manager.
 */
    public ListEnumConstantsTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "list_enum_constants"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Lists all constants (values) of an enum type."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "className", "string", "Fully qualified enum class name");
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "className"); }

/**
 * Handles the {@code list_enum_constants} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        var entry = findEntry(name);

        var type = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        if (!type.isEnum()) {
            return error("'" + className + "' is not an enum.");
        }

        CtEnum<?> enumType = (CtEnum<?>) type;
        var values = enumType.getEnumValues();

        StringBuilder sb = new StringBuilder();
        sb.append("# Enum Constants: `").append(className).append("`\n\n");
        sb.append("**Total:** ").append(values.size()).append(" constants\n\n");

        for (var val : values) {
            sb.append("- `").append(val.getSimpleName()).append("`");
            if (val.getDefaultExpression() != null) {
                sb.append(" = ").append(val.getDefaultExpression());
            }
            sb.append("\n");
        }

        return ok(sb);
    }
}
