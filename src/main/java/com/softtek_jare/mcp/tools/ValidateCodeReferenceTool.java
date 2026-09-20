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
import java.util.stream.Collectors;

/**
 * The {@code ValidateCodeReferenceTool} class.
 *
 * @author Alejandro Ferreira
 */
public class ValidateCodeReferenceTool extends BaseJavaTool {

/**
 * Constructs the {@code ValidateCodeReferenceTool} with the given project manager.
 */
    public ValidateCodeReferenceTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "validate_code_reference"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Validates whether a specific type, method, field, or constructor exists in the project. Returns a clear EXISTS / does NOT exist result."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name"),
            "memberName", Map.of("type", "string", "description", "Optional: method, field, or constructor name to validate"),
            "signature", Map.of("type", "string", "description", "Optional: parameter types for disambiguation (e.g. 'String,int')"),
            "memberKind", Map.of("type", "string", "description", "Optional: 'method', 'field', or 'constructor'")
        );
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "className"); }

/**
 * Handles the {@code validate_code_reference} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String memberName = arg(request, "memberName");
        String signature = arg(request, "signature");
        String memberKind = arg(request, "memberKind");
        var entry = findEntry(name);

        var type = entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElse(null);

        if (type == null) {
            return ok("RESULT: type `" + className + "` does **NOT** exist in the project.");
        }

        if (memberName == null) {
            return ok("RESULT: type `" + className + "` **EXISTS** in the project.");
        }

        if ("field".equals(memberKind)) {
            boolean found = type.getFields().stream().anyMatch(f -> f.getSimpleName().equals(memberName));
            if (found) {
                return ok("RESULT: field `" + className + "." + memberName + "` **EXISTS**.");
            }
            return ok("RESULT: field `" + className + "." + memberName + "` does **NOT** exist.");
        }

        if ("constructor".equals(memberKind)) {
            if (!type.isClass()) {
                return ok("RESULT: `" + className + "` is not a class (cannot have constructors).");
            }
            var ctClass = (spoon.reflect.declaration.CtClass<?>) type;
            var matching = ctClass.getConstructors().stream()
                    .filter(c -> {
                        if (signature == null || signature.isBlank()) return true;
                        String sig = c.getParameters().stream()
                                .map(p -> p.getType().getSimpleName())
                                .collect(Collectors.joining(","));
                        return sig.equals(signature);
                    })
                    .toList();
            if (!matching.isEmpty()) {
                return ok("RESULT: constructor `" + className + "(" + (signature != null ? signature : "...") + ")` **EXISTS**.");
            }
            if (signature != null) {
                return ok("RESULT: constructor with signature `(" + signature + ")` does **NOT** exist.");
            }
            return ok("RESULT: class `" + className + "` has no explicit constructors (default constructor may exist).");
        }

        // Default: check methods
        var methods = type.getMethods().stream()
                .filter(m -> m.getSimpleName().equals(memberName))
                .toList();

        if (methods.isEmpty()) {
            return ok("RESULT: method `" + className + "." + memberName + "(...)` does **NOT** exist.");
        }

        if (signature != null && !signature.isBlank()) {
            for (var m : methods) {
                String sig = m.getParameters().stream()
                        .map(p -> p.getType().getSimpleName())
                        .collect(Collectors.joining(","));
                if (sig.equals(signature)) {
                    return ok("RESULT: method `" + className + "." + memberName + "(" + signature + ")` **EXISTS**.");
                }
            }
            return ok("RESULT: method `" + className + "." + memberName + "(...)` exists but **NO** overload with signature `(" + signature + ")`. Available overloads: "
                    + methods.size() + ".");
        }

        return ok("RESULT: method `" + className + "." + memberName + "(...)` **EXISTS** (" + methods.size() + " overload(s)).");
    }
}
