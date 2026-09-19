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
import com.softtek_jare.mcp.edit.LineEndings;
import com.softtek_jare.mcp.model.ProjectEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The {@code EditLineTool} — replaces a single line in a source file.
 *
 * @author Janusch Rentenatus
 */
public class EditLineTool extends BaseJavaTool {

    private final EditManager editManager;

    /**
     * Constructs the {@code EditLineTool} with the given project manager and edit manager.
     */
    public EditLineTool(ProjectManager manager, EditManager editManager) {
        super(manager);
        this.editManager = editManager;
    }

    @Override protected String toolName() { return "edit_line"; }
    @Override protected String toolDescription() {
        return "Replace a single line in a source file. CR characters are stripped from both "
                + "the file content and newContent before matching, so the edit works regardless "
                + "of CRLF or LF. The written line adopts the file's existing line ending.";
    }
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "file", Map.of("type", "string", "description", "Path to the source file (relative to project source dir, or absolute)"),
            "lineNumber", Map.of("type", "integer", "description", "1-indexed line number to replace"),
            "newContent", Map.of("type", "string", "description", "New content for the line (without line ending)"),
            "expectedFingerprint", Map.of("type", "string", "description", "Optional: fingerprint for optimistic locking")
        );
    }
    @Override protected List<String> toolRequired() { return req("name", "file", "lineNumber", "newContent"); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception {
        String name = arg(request, "name");
        String filePath = arg(request, "file");
        String expectedFingerprint = arg(request, "expectedFingerprint");
        int lineNumber;
        try {
            lineNumber = Integer.parseInt(String.valueOf(request.arguments().get("lineNumber")));
        } catch (NumberFormatException e) {
            return error("lineNumber must be an integer");
        }
        if (lineNumber < 1) return error("lineNumber must be >= 1");

        String newContent = arg(request, "newContent");
        if (newContent == null) newContent = "";

        ProjectEntry entry = findEntry(name);
        requireEditable(entry);

        Path file = resolveFile(entry, filePath);
        if (!Files.exists(file)) {
            return error("File not found: " + file);
        }

        // Read, normalize CR, split into lines
        String normalized = LineEndings.readNormalized(file);
        String[] lines = normalized.split("\n", -1);

        if (lineNumber > lines.length) {
            return error("File has " + lines.length + " line(s); cannot replace line " + lineNumber);
        }

        // Normalize CR from newContent as well
        String normalizedNewContent = LineEndings.normalizeForMatch(newContent);
        lines[lineNumber - 1] = normalizedNewContent;

        String newFileContent = String.join("\n", lines);

        // Write via EditManager (preserves line ending, validates fingerprint)
        ProjectEntry updated = editManager.writeFile(entry, file, newFileContent, expectedFingerprint);
        manager.updateEntry(updated);
        editManager.logEdit(toolName());

        StringBuilder sb = new StringBuilder();
        sb.append("Line ").append(lineNumber).append(" replaced in ").append(file.getFileName()).append(".\n");
        sb.append(formatMultiModuleWarning(updated));
        return ok(sb);
    }

    /**
     * Resolves a file path relative to the project source directory, or uses absolute path.
     */
    private Path resolveFile(ProjectEntry entry, String filePath) {
        Path p = Path.of(filePath);
        if (p.isAbsolute()) return p;
        // Try project source dir first
        Path srcDir = entry.projectDir();
        if (srcDir != null) {
            Path resolved = srcDir.resolve(filePath);
            if (Files.exists(resolved)) return resolved;
        }
        // Try original project dir
        if (entry.originalProjectDir() != null) {
            Path resolved = entry.originalProjectDir().resolve(filePath);
            if (Files.exists(resolved)) return resolved;
        }
        return p;
    }
}
