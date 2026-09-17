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
import java.nio.file.StandardCopyOption;
import java.util.Map;

import spoon.reflect.declaration.CtType;

/**
 * The {@code MoveClassTool} — moves a class to a different package.
 *
 * @author Janusch Rentenatus
 */
public class MoveClassTool extends BaseJavaTool {

    private final EditManager editManager;

    public MoveClassTool(ProjectManager manager, EditManager editManager) {
        super(manager);
        this.editManager = editManager;
    }

    @Override protected String toolName() { return "move_class"; }
    @Override protected String toolDescription() {
        return "Move a class to a different package. Rewrites package declaration and updates "
                + "imports across all loaded projects.";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "newPackage", Map.of("type", "string", "description", "Target package name")
        );
    }
    @Override protected java.util.List<String> toolRequired() { return req("name", "className", "newPackage"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String newPackage = arg(request, "newPackage");

        ProjectEntry entry = findEntry(name);
        requireEditable(entry);

        CtType<?> targetType = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        Path oldFile = targetType.getPosition().getFile() != null
                ? targetType.getPosition().getFile().toPath() : null;
        if (oldFile == null) return error("Cannot determine source file.");

        String oldPackage = targetType.getPackage() != null
                ? targetType.getPackage().getQualifiedName() : "";
        String simpleName = targetType.getSimpleName();

        Path srcDir = entry.projectDir();
        if (srcDir == null) srcDir = entry.originalProjectDir();
        if (srcDir == null) return error("Cannot determine source directory.");

        Path newPackageDir = srcDir.resolve(newPackage.replace(".", "/"));
        Path newFile = newPackageDir.resolve(simpleName + ".java");

        // Read old source, rewrite package declaration
        String source = Files.readString(oldFile);
        String newSource = source.replace("package " + oldPackage + ";",
                "package " + newPackage + ";");

        // Write to new location
        Files.createDirectories(newPackageDir);
        Files.writeString(newFile, newSource);

        // Delete old file
        Files.delete(oldFile);
        manager.markDirty(name);
        editManager.logEdit(toolName());

        // Update imports in all loaded source files
        String oldQualified = oldPackage + "." + simpleName;
        String newQualified = newPackage + "." + simpleName;
        int importsUpdated = 0;
        for (CtType<?> type : entry.model().getAllTypes()) {
            Path file = type.getPosition().getFile() != null
                    ? type.getPosition().getFile().toPath() : null;
            if (file == null || file.equals(newFile)) continue;
            if (!Files.exists(file)) continue; // skip deleted files (old class location)
            String content = Files.readString(file);
            if (content.contains(oldQualified)) {
                String updatedContent = content.replace(oldQualified, newQualified);
                if (!updatedContent.equals(content)) {
                    entry = editManager.writeFile(entry, file, updatedContent, null);
                    manager.updateEntry(entry);
        editManager.logEdit(toolName());
                    importsUpdated++;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Moved: ").append(className).append(" -> ").append(newQualified).append("\n");
        sb.append("Imports updated: ").append(importsUpdated).append("\n");
        sb.append(formatMultiModuleWarning(entry));
        return ok(sb);
    }
}
