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
 * The {@code InspectBuildConfigTool} class.
 *
 * @author Alejandro Ferreira
 */
public class InspectBuildConfigTool extends BaseJavaTool {

/**
 * Constructs the {@code InspectBuildConfigTool} with the given project manager.
 */
    public InspectBuildConfigTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "inspect_build_config"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Shows the detected build configuration for a loaded Java project."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() { return props("name", "string"); }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name"); }

/**
 * Handles the {@code inspect_build_config} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        var entry = findEntry(name);
        var projectDir = entry.projectDir();

        StringBuilder sb = new StringBuilder();
        sb.append("# Build Configuration: ").append(entry.name()).append("\n\n");
        sb.append("| Aspect | Value |\n");
        sb.append("|--------|-------|\n");
        sb.append("| **Build type** | ").append(entry.buildType()).append(" |\n");
        sb.append("| **Project dir** | ").append(projectDir).append(" |\n");

        sb.append("\n## Detected Files\n\n");
        String[] markers = {"pom.xml", "build.gradle", "build.gradle.kts", ".project", ".classpath", "build.xml", "ivy.xml"};
        for (String marker : markers) {
            boolean found = projectDir.resolve(marker).toFile().exists();
            sb.append("- `").append(marker).append("`: ").append(found ? "✅ found" : "—").append("\n");
        }

        return ok(sb);
    }
}
