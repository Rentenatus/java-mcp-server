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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The {@code GetFileContentTool} class.
 *
 * @author Alejandro Ferreira
 */
public class GetFileContentTool extends BaseJavaTool {

/**
 * Constructs the {@code GetFileContentTool} with the given project manager.
 */
    public GetFileContentTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "get_file_content"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Reads the raw source content of the file containing a given type."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return propsWithDescription("name", "string", "Project name or alias",
                "className", "string", "Fully qualified class name");
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "className"); }

/**
 * Handles the {@code get_file_content} tool invocation and returns the result.
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

        Path filePath = null;

        if (type.getPosition() != null && type.getPosition().getFile() != null) {
            filePath = type.getPosition().getFile().toPath().normalize();
        }

        if (filePath == null || !Files.exists(filePath)) {
            String packagePath = type.getPackage() != null
                    ? type.getPackage().getQualifiedName().replace('.', '/')
                    : "";
            Path expected = entry.projectDir().resolve("src/main/java")
                    .resolve(packagePath)
                    .resolve(type.getSimpleName() + ".java");
            if (Files.exists(expected)) {
                filePath = expected;
            }
        }

        if (filePath == null || !Files.exists(filePath)) {
            return domainError("DOMAIN_ERROR", "Could not locate source file for " + className);
        }

        try {
            String content = Files.readString(filePath);
            StringBuilder sb = new StringBuilder();
            sb.append("### File: ").append(filePath).append("\n\n");
            sb.append("```java\n").append(content);
            if (!content.endsWith("\n")) {
                sb.append("\n");
            }
            sb.append("```");
            return ok(sb);
        } catch (Exception e) {
            return domainError("DOMAIN_ERROR", "Failed to read file: " + e.getMessage());
        }
    }
}
