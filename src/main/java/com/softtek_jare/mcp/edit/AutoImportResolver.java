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

package com.softtek_jare.mcp.edit;

import com.softtek_jare.mcp.model.ProjectEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

/**
 * Resolves type references in a method body against the project's loaded AST
 * and automatically inserts necessary import statements.
 *
 * @author Janusch Rentenatus
 */
public class AutoImportResolver {

    /**
     * Result of import resolution: either resolved imports to add, or
     * unresolved type names that block the edit.
     */
    public record Result(List<String> importsToAdd, List<String> unresolvedTypes) {}

    /**
     * Analyzes a method body for type references that are not resolvable in
     * the current import scope, and searches the project for matching types.
     *
     * @param entry    the loaded project entry
     * @param targetClass the class where the method body will be placed
     * @param bodyText the new method body text
     * @return resolution result with imports to add and/or unresolved types
     */
    public Result resolve(ProjectEntry entry, CtType<?> targetClass, String bodyText) {
        Set<String> alreadyImported = collectImportedTypeNames(targetClass);
        Set<String> projectTypeNames = collectProjectTypeNames(entry);
        Set<String> javaLangTypes = collectJavaLangTypes();

        // Extract potential type names from body text (capitalized identifiers)
        Set<String> potentialTypes = extractTypeNames(bodyText);

        List<String> importsToAdd = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();

        for (String simpleName : potentialTypes) {
            // Skip if already imported or in java.lang or same package
            if (alreadyImported.contains(simpleName)) continue;
            if (javaLangTypes.contains(simpleName)) continue;
            if (targetClass.getPackage() != null) {
                // Check if type is in same package
                String samePackageQualified = targetClass.getPackage().getQualifiedName() + "." + simpleName;
                if (projectTypeNames.contains(samePackageQualified)) continue;
            }
            // Check if it's the target class itself
            if (targetClass.getSimpleName().equals(simpleName)) continue;

            // Search project types for a match
            String qualified = findInProject(entry, simpleName);
            if (qualified != null) {
                importsToAdd.add(qualified);
            } else {
                unresolved.add(simpleName);
            }
        }

        return new Result(importsToAdd, unresolved);
    }

    private Set<String> collectImportedTypeNames(CtType<?> type) {
        Set<String> names = new HashSet<>();
        // Best-effort: collect simple names of types in the same model that are
        // directly referenced by the target class
        if (type.getFactory() != null) {
            for (var ref : type.getReferencedTypes()) {
                if (ref.getQualifiedName() != null) {
                    names.add(ref.getSimpleName());
                }
            }
        }
        return names;
    }

    private Set<String> collectProjectTypeNames(ProjectEntry entry) {
        Set<String> names = new HashSet<>();
        if (entry.model() != null) {
            for (CtType<?> t : entry.model().getAllTypes()) {
                names.add(t.getQualifiedName());
            }
        }
        return names;
    }

    private Set<String> collectJavaLangTypes() {
        return Set.of(
            "String", "Integer", "Long", "Double", "Float", "Boolean",
            "Object", "System", "Math", "Thread", "Exception", "RuntimeException",
            "Class", "Comparable", "Number", "Character", "Void", "Override",
            "Deprecated", "SuppressWarnings", "Iterable", "AutoCloseable",
            "StringBuilder", "StringBuffer"
        );
    }

    private Set<String> extractTypeNames(String bodyText) {
        Set<String> names = new HashSet<>();
        // Match capitalized identifiers that look like type names
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "\\b([A-Z][a-zA-Z0-9_]*)\\b");
        java.util.regex.Matcher m = p.matcher(bodyText);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private String findInProject(ProjectEntry entry, String simpleName) {
        if (entry.model() == null) return null;
        for (CtType<?> t : entry.model().getAllTypes()) {
            if (t.getSimpleName().equals(simpleName)) {
                return t.getQualifiedName();
            }
        }
        return null;
    }
}
