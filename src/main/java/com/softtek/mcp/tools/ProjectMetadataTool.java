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

/**
 * The {@code ProjectMetadataTool} class.
 *
 * @author Alejandro Ferreira
 */
public class ProjectMetadataTool extends BaseJavaTool {

/**
 * Constructs the {@code ProjectMetadataTool} with the given project manager.
 */
    public ProjectMetadataTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "project_metadata"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Retrieves metadata about a loaded Java project."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() { return props("name", "string"); }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name"); }

/**
 * Handles the {@code project_metadata} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        var entry = findEntry(name);
        var model = entry.model();

        long classCount = model.getAllTypes().stream().filter(t -> t.isClass()).count();
        long interfaceCount = model.getAllTypes().stream().filter(t -> t.isInterface()).count();
        long enumCount = model.getAllTypes().stream().filter(t -> t.isEnum()).count();
        long methodCount = model.getAllTypes().stream().mapToLong(t -> t.getMethods().size()).sum();
        long fieldCount = model.getAllTypes().stream().mapToLong(t -> t.getFields().size()).sum();
        long packageCount = model.getAllPackages().size();

        StringBuilder sb = new StringBuilder();
        sb.append("# Project Metadata: ").append(entry.name()).append("\n\n");
        sb.append("| Field | Value |\n");
        sb.append("|-------|-------|\n");
        sb.append("| **Name** | ").append(entry.name()).append(" |\n");
        if (!entry.alias().equals(entry.name())) {
            sb.append("| **Alias** | ").append(entry.alias()).append(" |\n");
        }
        sb.append("| **Build** | ").append(entry.buildType()).append(" |\n");
        sb.append("| **Directory** | ").append(entry.projectDir()).append(" |\n");
        sb.append("| **Packages** | ").append(packageCount).append(" |\n");
        sb.append("| **Classes** | ").append(classCount).append(" |\n");
        sb.append("| **Interfaces** | ").append(interfaceCount).append(" |\n");
        sb.append("| **Enums** | ").append(enumCount).append(" |\n");
        sb.append("| **Methods** | ").append(methodCount).append(" |\n");
        sb.append("| **Fields** | ").append(fieldCount).append(" |\n");

        return ok(sb);
    }
}
