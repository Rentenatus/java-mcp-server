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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The {@code ListClassesTool} class.
 *
 * @author Alejandro Ferreira
 */
public class ListClassesTool extends BaseJavaTool {

/**
 * Constructs the {@code ListClassesTool} with the given project manager.
 */
    public ListClassesTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "list_classes"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Lists all classes, interfaces, and enums in a loaded Java project, optionally filtered by package."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "package", "string", "Optional: filter by package qualified name");
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name"); }

/**
 * Handles the {@code list_classes} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String pkgFilter = arg(request, "package");
        var entry = findEntry(name);

        var types = entry.model().getAllTypes().stream()
                .filter(t -> pkgFilter == null || pkgFilter.isBlank()
                        || (t.getPackage() != null && pkgFilter.equals(t.getPackage().getQualifiedName())))
                .sorted(Comparator.comparing(t -> t.getQualifiedName()))
                .toList();

        StringBuilder sb = new StringBuilder("# Classes");
        if (pkgFilter != null && !pkgFilter.isBlank()) {
            sb.append(" in `").append(pkgFilter).append("`");
        }
        sb.append(" (").append(entry.name()).append(")\n\n");
        sb.append("**Total:** ").append(types.size()).append(" types\n\n");

        for (var type : types) {
            String kind;
            if (type.isClass()) kind = "CLASS";
            else if (type.isInterface()) kind = "INTERFACE";
            else if (type.isEnum()) kind = "ENUM";
            else if (type.isAnnotationType()) kind = "ANNOTATION";
            else kind = "TYPE";

            sb.append("- **").append(type.getQualifiedName()).append("**");
            sb.append(" (").append(kind).append(", ").append(type.getMethods().size()).append(" methods)");
            if (type.isPublic()) sb.append(" `public`");
            sb.append("\n");
        }

        return ok(sb);
    }
}
