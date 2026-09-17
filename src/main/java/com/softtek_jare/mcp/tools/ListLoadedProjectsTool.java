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

/**
 * The {@code ListLoadedProjectsTool} class.
 *
 * @author Alejandro Ferreira
 * @author Janusch Rentenatus
 */
public class ListLoadedProjectsTool extends BaseJavaTool {

/**
 * Constructs the {@code ListLoadedProjectsTool} with the given project manager.
 */
    public ListLoadedProjectsTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "list_loaded_projects"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Lists all Java projects currently loaded in memory."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() { return Map.of(); }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return List.of(); }

/**
 * Handles the {@code list_loaded_projects} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        var entries = manager.list();
        if (entries.isEmpty()) {
            return ok("# Loaded Projects\n\n_No projects loaded._");
        }

        StringBuilder sb = new StringBuilder("# Loaded Projects\n\n");
        for (var entry : entries) {
            String types = String.valueOf(entry.model().getAllTypes().size());
            sb.append("- **").append(entry.name()).append("**");
            if (!entry.alias().equals(entry.name())) {
                sb.append(" (alias: `").append(entry.alias()).append("`)");
            }
            sb.append(" — ").append(types).append(" types, build: ").append(entry.buildType());
            if (entry.expiryDate() != null) {
                sb.append(" [expires: ").append(entry.expiryDate()).append("]");
            if (entry.expired()) {
                sb.append(" **[EXPIRED]**");
            }
            }
            sb.append("\n");
        }
        return ok(sb);
    }
}
