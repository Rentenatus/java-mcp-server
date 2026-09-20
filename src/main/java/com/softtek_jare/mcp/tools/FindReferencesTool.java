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

import java.util.*;
import java.util.stream.Collectors;

import spoon.reflect.code.*;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * The {@code FindReferencesTool} class.
 *
 * @author Alejandro Ferreira
 */
public class FindReferencesTool extends BaseJavaTool {

/**
 * Constructs the {@code FindReferencesTool} with the given project manager.
 */
    public FindReferencesTool(ProjectManager manager) {
        super(manager);
    }

/**
 * Returns the name of this tool.
 */
    @Override protected String toolName() { return "find_references"; }
/**
 * Returns the description of this tool.
 */
    @Override protected String toolDescription() { return "Finds all references to a given type, method, field, or constructor across the project."; }
/**
 * Returns the input schema properties for this tool.
 */
    @Override protected Map<String, Object> toolProperties() {
        return Map.of(
            "name", Map.of("type", "string", "description", "Project name or alias"),
            "className", Map.of("type", "string", "description", "Fully qualified class name of the target"),
            "memberName", Map.of("type", "string", "description", "Optional: method, field name, or '<init>' for constructor"),
            "memberKind", Map.of("type", "string", "description", "Optional: 'method', 'field', 'type', or 'constructor' (default: all)")
        );
    }
/**
 * Returns the list of required argument keys for this tool.
 */
    @Override protected List<String> toolRequired() { return req("name", "className"); }

/**
 * Handles the {@code find_references} tool invocation and returns the result.
 */
    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String name = arg(request, "name");
        String className = arg(request, "className");
        String memberName = arg(request, "memberName");
        String memberKind = arg(request, "memberKind");
        var entry = findEntry(name);

        entry.model().getAllTypes().stream()
                .filter(t -> t.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type not found: " + className));

        StringBuilder sb = new StringBuilder();
        String title = memberName != null ? className + "." + memberName : className;
        sb.append("# References to `").append(title).append("`\n\n");

        int total = 0;
        boolean searchMethods = memberKind == null || "method".equals(memberKind);
        boolean searchFields = memberKind == null || "field".equals(memberKind);
        boolean searchType = memberKind == null || "type".equals(memberKind);
        boolean searchCtors = memberKind == null || "constructor".equals(memberKind);

        for (var type : entry.model().getAllTypes().stream()
                .sorted(Comparator.comparing(t -> t.getQualifiedName())).toList()) {

            List<String> refs = new ArrayList<>();

            for (var method : type.getMethods()) {
                if (method.getBody() == null) continue;

                if (searchMethods) {
                    List<CtInvocation<?>> invocations = method.getBody().getElements(new TypeFilter<>(CtInvocation.class));
                    for (var inv : invocations) {
                        var exec = inv.getExecutable();
                        if (exec.getDeclaringType() != null
                                && exec.getDeclaringType().getQualifiedName().equals(className)
                                && (memberName == null || exec.getSimpleName().equals(memberName))) {
                            refs.add("- Method `" + method.getSimpleName() + "(...)` calls `"
                                    + exec.getSimpleName() + "(...)`");
                            total++;
                        }
                    }
                }

                if (searchFields) {
                    List<CtFieldAccess<?>> fieldAccesses = method.getBody().getElements(new TypeFilter<>(CtFieldAccess.class));
                    for (var fa : fieldAccesses) {
                        var field = fa.getVariable();
                        if (field.getDeclaringType() != null
                                && field.getDeclaringType().getQualifiedName().equals(className)
                                && (memberName == null || field.getSimpleName().equals(memberName))) {
                            String accessType = (fa instanceof CtFieldWrite) ? "writes" : "reads";
                            refs.add("- Method `" + method.getSimpleName() + "(...)` " + accessType + " `"
                                    + field.getSimpleName() + "`");
                            total++;
                        }
                    }
                }

                if (searchCtors && "constructor".equals(memberKind) || (searchCtors && memberName == null)) {
                    List<CtConstructorCall<?>> ctorCalls = method.getBody().getElements(new TypeFilter<>(CtConstructorCall.class));
                    for (var cc : ctorCalls) {
                        var exec = cc.getExecutable();
                        if (exec.getDeclaringType() != null
                                && exec.getDeclaringType().getQualifiedName().equals(className)
                                && (memberName == null || "<init>".equals(memberName))) {
                            refs.add("- Method `" + method.getSimpleName() + "(...)` instantiates `new "
                                    + exec.getDeclaringType().getSimpleName() + "(...)`");
                            total++;
                        }
                    }
                }
            }

            if (searchType && (memberKind == null || "type".equals(memberKind))) {
                if (memberName == null) {
                    for (var method : type.getMethods()) {
                        if (method.getType() != null) {
                            String retType = method.getType().getQualifiedName();
                            if (retType.equals(className)) {
                                refs.add("- Method `" + method.getSimpleName() + "(...)` returns `" + className + "`");
                                total++;
                            }
                        }
                        for (var p : method.getParameters()) {
                            if (p.getType() != null && className.equals(p.getType().getQualifiedName())) {
                                refs.add("- Method `" + method.getSimpleName() + "(...)` has parameter of type `" + className + "`");
                                total++;
                            }
                        }
                    }
                    for (var field : type.getFields()) {
                        if (field.getType() != null) {
                            String fieldType = field.getType().getQualifiedName();
                            if (fieldType.equals(className)) {
                                refs.add("- Field `" + field.getSimpleName() + "` has type `" + className + "`");
                                total++;
                            }
                        }
                    }
                }
            }

            if (!refs.isEmpty()) {
                sb.append("## ").append(type.getQualifiedName()).append("\n");
                for (String ref : refs) {
                    sb.append(ref).append("\n");
                }
                sb.append("\n");
            }
        }

        if (total == 0) {
            sb.append("_No references found._");
        } else {
            sb.append("\n**Total references:** ").append(total);
        }

        return ok(sb);
    }
}
