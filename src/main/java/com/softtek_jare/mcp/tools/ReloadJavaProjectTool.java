/*
 * MIT License
 *
 * Copyright (c) 2026 Janusch Rentenatus
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
import com.softtek_jare.mcp.model.ProjectEntry;
import com.softtek_jare.mcp.model.ProjectLoadException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The {@code ReloadJavaProjectTool} class.
 *
 * @author Janusch Rentenatus
 */
public class ReloadJavaProjectTool extends BaseJavaTool {

    public ReloadJavaProjectTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "reload_java_project"; }
    @Override protected String toolDescription() {
        return "Reloads previously loaded Java project(s) from their original source, replacing "
            + "the in-memory model with a fresh parse. Use when check_project_dirty or a scoped "
            + "dirty warning indicates stale source files, or when a tool warns about expired projects. "
            + "Parameters: name (project to reload, required unless expired=true), expired (if true, "
            + "reloads ALL expired projects; default false). Projects are reloaded with the same "
            + "parameters as the original load. The expiry timer is reset on reload. "
            + "Note: New files created after the initial load are not visible in the model and cannot "
            + "be detected by scoped dirty checks. Only check_project_dirty performs a full scan. "
            + "After reload, all new, changed, and deleted files are reflected in the model.";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias to reload (required unless expired=true)"),
            "expired", Map.of("type", "boolean", "description", "If true, reloads ALL expired projects (default false)")
        );
    }
    @Override protected List<String> toolRequired() { return List.of(); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        Boolean expiredArg = (Boolean) request.arguments().get("expired");
        boolean expired = expiredArg != null && expiredArg;

        List<String> reloadedNames = new ArrayList<>();

        if (expired) {
            try {
                var reloaded = manager.reloadExpired();
                for (ProjectEntry e : reloaded) reloadedNames.add(e.name());

                // Also reload the named project if provided and not already in the expired set
                if (name != null && !name.isBlank()) {
                    var existing = manager.find(name);
                    if (existing != null && !existing.expired() && !reloadedNames.contains(existing.name())) {
                        var e = manager.reload(name);
                        reloadedNames.add(e.name());
                    }
                }
            } catch (ProjectLoadException e) {
                return error("Reload failed: " + e.getMessage());
            }
        } else {
            if (name == null || name.isBlank()) {
                return error("Parameter 'name' is required when expired is false or not set.");
            }
            try {
                var entry = manager.reload(name);
                reloadedNames.add(entry.name());
            } catch (ProjectLoadException e) {
                return error("Reload failed: " + e.getMessage());
            }
        }

        if (reloadedNames.isEmpty()) {
            return ok("No expired projects to reload.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Project Reload Complete\\n\\n");
        sb.append("**Reloaded:** ").append(reloadedNames.size()).append(" project(s)\\n\\n");
        for (String n : reloadedNames) {
            var entry = manager.find(n);
            if (entry != null) {
                sb.append("- **").append(n).append("** — ")
                  .append(entry.model().getAllTypes().size()).append(" types, build: ")
                  .append(entry.buildType());
                if (entry.delomboked()) sb.append(" (delomboked)");
                sb.append("\\n");
            }
        }
        sb.append("\\nExpiry timer reset for all reloaded projects.\\n");
        return ok(sb);
    }
}
