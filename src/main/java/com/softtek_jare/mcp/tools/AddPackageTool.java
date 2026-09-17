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
import com.softtek_jare.mcp.edit.EditManager;
import com.softtek_jare.mcp.model.ProjectEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The {@code AddPackageTool} — creates a new package directory.
 *
 * @author Janusch Rentenatus
 */
public class AddPackageTool extends BaseJavaTool {

    public AddPackageTool(ProjectManager manager) {
        super(manager);
    }

    @Override protected String toolName() { return "add_package"; }
    @Override protected String toolDescription() {
        return "Create a new package. Creates the directory structure and optionally generates package-info.java.";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "packageName", Map.of("type", "string", "description", "Fully qualified package name (e.g. 'com.example.service')"),
            "generatePackageInfo", Map.of("type", "boolean", "description", "If true, generates package-info.java")
        );
    }
    @Override protected java.util.List<String> toolRequired() { return req("name", "packageName"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception {
        String name = arg(request, "name");
        String packageName = arg(request, "packageName");
        boolean genInfo = boolArg(request, "generatePackageInfo");

        ProjectEntry entry = findEntry(name);
        requireEditable(entry);

        Path srcDir = entry.projectDir();
        if (srcDir == null) srcDir = entry.originalProjectDir();
        if (srcDir == null) return error("Cannot determine source directory.");

        Path packageDir = srcDir.resolve(packageName.replace(".", "/"));
        if (Files.exists(packageDir)) {
            return error("Package '" + packageName + "' already exists at " + packageDir);
        }
        Files.createDirectories(packageDir);

        if (genInfo) {
            Path infoFile = packageDir.resolve("package-info.java");
            Files.writeString(infoFile, "package " + packageName + ";\n\n");
        }
        manager.markDirty(name);

        return ok("Package created: " + packageName + " at " + packageDir
                + (genInfo ? " (with package-info.java)" : "")
                + "\n" + formatMultiModuleWarning(entry));
    }
}
