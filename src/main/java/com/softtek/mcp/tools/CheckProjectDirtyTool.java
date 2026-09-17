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

package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek.mcp.ProjectManager;
import com.softtek.mcp.VersionLoader;

import java.util.List;
import java.util.Map;

/**
 * The {@code CheckProjectDirtyTool} class.
 *
 * @author Janusch Rentenatus
 */
public class CheckProjectDirtyTool extends BaseJavaTool {

    public CheckProjectDirtyTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "check_project_dirty"; }
    @Override protected String toolDescription() {
        return "Performs a full scan of the project source root, comparing every .java file on disk "
            + "against stored fingerprints. Detects changed, deleted, and new files since load. "
            + "This is the central status tool — use it to check if a reload is needed.";
    }
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias");
    }
    @Override protected List<String> toolRequired() { return req("name"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        var entry = findEntry(name);

        DirtyCheckResult dirty = checkDirtyFullScan(entry);
        StringBuilder sb = new StringBuilder();
        sb.append("# Dirty Check: `").append(entry.name()).append("`\\n\\n");
        sb.append("**MCP Server Version:** ").append(VersionLoader.getVersion()).append("\\n\\n");

        if (!dirty.isDirty()) {
            sb.append("> ✅ **Project is clean** — ")
              .append(dirty.filesChecked())
              .append(" source files checked, no changes since load.\\n");
            return ok(sb);
        }

        int total = dirty.changedFiles().size() + dirty.deletedFiles().size() + dirty.newFiles().size();
        sb.append("> ⚠️ **Project is dirty** — ")
          .append(dirty.filesChecked()).append(" source files on disk, ")
          .append(entry.sourceFingerprints().size()).append(" in fingerprint map:\\n")
          .append("> ").append(dirty.changedFiles().size()).append(" changed, ")
          .append(dirty.deletedFiles().size()).append(" deleted, ")
          .append(dirty.newFiles().size()).append(" new since load.\\n\\n");

        if (!dirty.changedFiles().isEmpty()) {
            sb.append("| Status | File |\\n");
            sb.append("|--------|------|\\n");
            for (String f : dirty.changedFiles()) sb.append("| Changed | ").append(f).append(" |\\n");
            for (String f : dirty.deletedFiles()) sb.append("| Deleted | ").append(f).append(" |\\n");
            for (String f : dirty.newFiles()) sb.append("| New | ").append(f).append(" |\\n");
        }

        sb.append("\\nCall `reload_java_project` with name `").append(entry.name())
          .append("` to refresh the model.\\n");
        sb.append("\\n**Note:** New files created after the initial load are not visible in the model. ")
          .append("Only this full-scan tool detects them. After reload, all new, changed, and deleted ")
          .append("files are reflected in the model.\\n");

        return ok(sb);
    }
}
