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
 * The {@code InspectFieldTool} class.
 *
 * @author Alejandro Ferreira
 * @author Janusch Rentenatus
 */
public class InspectFieldTool extends BaseJavaTool {

/**
 * Constructs the {@code InspectFieldTool} with the given project manager.
 */
    public InspectFieldTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "inspect_field"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Deep-dives into a specific field's declaration including type, modifiers, annotations, and initializer."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "fieldName", Map.of("type", "string", "description", "Field name"),
            "withJavadoc", Map.of("type", "boolean", "description", "If true (default), includes the Javadoc comment in the output")
        );
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "className", "fieldName"); }

/**
 * Handles the {@code inspect_field} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String fieldName = arg(request, "fieldName");
        Boolean wj = (Boolean) request.arguments().get("withJavadoc");
        boolean withJavadoc = (wj == null) || wj;
        var entry = findEntry(name);

        var type = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + className));

        String dirtyWarning = formatDirtyWarning(checkDirty(entry, List.of(type)));

        var field = type.getFields().stream()
                .filter(f -> f.getSimpleName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Field '" + fieldName + "' not found in " + className));

        StringBuilder sb = new StringBuilder();
        sb.append("# Field: `").append(fieldName).append("` in `").append(className).append("`\n\n");

        if (withJavadoc) {
            String doc = field.getDocComment();
            if (doc != null && !doc.isBlank()) {
                sb.append("## Javadoc\n\n");
                sb.append("```\n");
                sb.append(doc.stripIndent().strip());
                sb.append("\n```\n\n");
            }
        }
        sb.append("| Property | Value |\n");
        sb.append("|----------|-------|\n");
        sb.append("| **Type** | `").append(field.getType()).append("` |\n");

        String visibility = field.isPublic() ? "public" : field.isProtected() ? "protected" : field.isPrivate() ? "private" : "package-private";
        sb.append("| **Visibility** | `").append(visibility).append("` |\n");
        if (field.isStatic()) sb.append("| **Static** | ✅ |\n");
        if (field.isFinal()) sb.append("| **Final** | ✅ |\n");
        if (field.isTransient()) sb.append("| **Transient** | ✅ |\n");
        if (field.isVolatile()) sb.append("| **Volatile** | ✅ |\n");

        if (field.getDefaultExpression() != null) {
            sb.append("| **Initializer** | `").append(field.getDefaultExpression()).append("` |\n");
        }

        var annotations = field.getAnnotations();
        if (!annotations.isEmpty()) {
            sb.append("\n## Annotations\n\n");
            for (var ann : annotations) {
                sb.append("- `@").append(ann.getAnnotationType().getQualifiedName()).append("`\n");
            }
        }

        sb.insert(0, dirtyWarning);
        return ok(sb);
    }
}
