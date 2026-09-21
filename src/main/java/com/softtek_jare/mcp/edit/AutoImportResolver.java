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
        return resolve(entry, targetClass, bodyText, java.util.Collections.emptyList());
    }

    /**
     * Analyzes a method body for type references, with an optional list of
     * manually supplied fully-qualified import names. When a potential type's
     * simple name matches the simple name of a manual FQN, the manual FQN is
     * treated as resolved and added to the import list — this lets the agent
     * disambiguate types that have the same simple name in different packages
     * (e.g. {@code java.util.List} vs {@code java.awt.List}) without writing
     * the FQN into the source text.
     *
     * <p>Manual FQNs are validated: a FQN that is neither a project type nor a
     * loadable JDK class is reported as unresolved so the agent cannot inject
     * a non-existent import that would fail to compile.
     *
     * @param entry         the loaded project entry
     * @param targetClass   the class where the method body will be placed
     * @param bodyText      the new method body text
     * @param manualImports optional fully-qualified names to force-resolve;
     *                      may be empty/null
     * @return resolution result with imports to add and/or unresolved types
     */
    public Result resolve(ProjectEntry entry, CtType<?> targetClass, String bodyText,
                          java.util.List<String> manualImports) {
        // targetClass may be null when creating a brand-new class (no existing
        // type yet). In that case there is no existing import scope, same-package
        // membership, or self-reference to skip.
        Set<String> alreadyImported = targetClass != null ? collectImportedTypeNames(targetClass) : new HashSet<>();
        Set<String> projectTypeNames = collectProjectTypeNames(entry);
        Set<String> javaLangTypes = collectJavaLangTypes();

        // Index manual imports by simple name for fast lookup. Only non-blank
        // FQNs are considered; duplicates collapse silently.
        java.util.Map<String, String> manualBySimple = new java.util.HashMap<>();
        if (manualImports != null) {
            for (String fqn : manualImports) {
                if (fqn == null || fqn.isBlank()) continue;
                String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
                if (!simple.isEmpty()) manualBySimple.putIfAbsent(simple, fqn);
            }
        }

        // Extract potential type names from body text (capitalized identifiers)
        Set<String> potentialTypes = extractTypeNames(bodyText);

        List<String> importsToAdd = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();

        for (String simpleName : potentialTypes) {
            // Skip if already imported or in java.lang or same package
            if (alreadyImported.contains(simpleName)) continue;
            if (javaLangTypes.contains(simpleName)) continue;
            if (targetClass != null && targetClass.getPackage() != null) {
                // Check if type is in same package
                String samePackageQualified = targetClass.getPackage().getQualifiedName() + "." + simpleName;
                if (projectTypeNames.contains(samePackageQualified)) continue;
            }
            // Check if it's the target class itself
            if (targetClass != null && targetClass.getSimpleName().equals(simpleName)) continue;

            // Manual import override: if the agent supplied a FQN whose simple
            // name matches, validate it (project type or loadable JDK class)
            // and treat it as resolved. This disambiguates types like
            // java.util.List vs java.awt.List without forcing the FQN into the
            // source text.
            String manualFqn = manualBySimple.get(simpleName);
            if (manualFqn != null) {
                if (isValidImport(entry, manualFqn)) {
                    importsToAdd.add(manualFqn);
                    continue;
                }
                // Invalid manual FQN — report as unresolved so the agent cannot
                // inject a non-existent import that would break compilation.
                unresolved.add(simpleName + " (manual import '" + manualFqn
                        + "' is not a project type or loadable JDK class)");
                continue;
            }

            // Search project types for a match
            List<String> matches = findInProject(entry, simpleName);
            if (matches.isEmpty()) {
                // Not in project — check if it's a JDK type. Two strategies:
                // 1. Try java.lang.<simpleName> (covers InterruptedException, etc.)
                // 2. Check if a fully-qualified name ending with .<simpleName>
                //    appears in the body text and can be loaded via Class.forName
                //    (covers javax.swing.Timer, java.awt.EventQueue, etc. when
                //    the user writes the FQN directly in the body).
                if (isJavaLangType(simpleName)) continue;
                if (isFullyQualifiedJdkType(bodyText, simpleName)) continue;
                unresolved.add(simpleName);
            } else if (matches.size() == 1) {
                importsToAdd.add(matches.get(0));
            } else {
                // Ambiguous: multiple types with the same simple name in different
                // packages. Report as unresolved with the candidates so the user
                // can pick the fully qualified name.
                unresolved.add(simpleName + " (ambiguous: " + String.join(" | ", matches) + ")");
            }
        }

        return new Result(importsToAdd, unresolved);
    }

    /**
     * Returns true if the fully-qualified name is either a project type or a
     * loadable JDK class. Used to validate manual imports so the agent cannot
     * inject a non-existent import that would break compilation.
     */
    private boolean isValidImport(ProjectEntry entry, String fqn) {
        // Check project types first
        if (entry.model() != null) {
            for (CtType<?> t : entry.model().getAllTypes()) {
                if (t.getQualifiedName().equals(fqn)) return true;
            }
        }
        // Check if it's a loadable JDK class
        try {
            Class.forName(fqn, false, ClassLoader.getSystemClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private Set<String> collectImportedTypeNames(CtType<?> type) {
        Set<String> names = new HashSet<>();
        if (type == null) return names;
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
        // Match capitalized identifiers that look like type names, but only in
        // code regions. String/char literals and comments are blanked out first
        // so that words like "Failed" in throw new Exception("Failed") or
        // "TODO" in a // TODO comment are not mistaken for unresolved types.
        String codeOnly = blankNonCode(bodyText);
        // Blank static constant/field accesses like Color.WHITE, Math.PI,
        // BorderLayout.CENTER so the member name is not mistaken for a type.
        // Only all-caps identifiers after a dot are blanked; mixed-case
        // members like Map.Entry are preserved (Entry could be a type).
        codeOnly = blankStaticAccess(codeOnly);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "\\b([A-Z][a-zA-Z0-9_]*)\\b");
        java.util.regex.Matcher m = p.matcher(codeOnly);
        while (m.find()) {
            String name = m.group(1);
            // Skip ALL_CAPS constants like MAX_VALUE, DEFAULT_TIMEOUT.
            // These follow SCREAMING_SNAKE_CASE (uppercase + underscore)
            // and are not type names. Single-word all-caps identifiers like
            // URL or JSON are kept because they could be type names.
            if (name.indexOf('_') >= 0 && name.equals(name.toUpperCase())) continue;
            names.add(name);
        }
        return names;
    }

    /**
     * Returns a copy of {@code text} with static constant/field accesses
     * ({@code .UPPERCASE_IDENTIFIER}) replaced by spaces, preserving length
     * and line structure. This prevents member names like {@code WHITE} in
     * {@code Color.WHITE} from being mistaken for unresolved type names.
     * Only all-caps identifiers after a dot are blanked; mixed-case member
     * accesses like {@code Map.Entry} are preserved.
     */
    private static String blankStaticAccess(String text) {
        if (text == null) return null;
        char[] out = text.toCharArray();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "\\.([A-Z][A-Z0-9_]*)\\b");
        java.util.regex.Matcher m = p.matcher(text);
        while (m.find()) {
            for (int i = m.start(1); i < m.end(1); i++) {
                out[i] = ' ';
            }
        }
        return new String(out);
    }

    /**
     * Checks whether a simple name corresponds to a {@code java.lang} type
     * by attempting {@code Class.forName("java.lang." + simpleName)}.
     * This catches types not in the hardcoded list, such as
     * {@code InterruptedException}, {@code NullPointerException}, etc.
     */
    private boolean isJavaLangType(String simpleName) {
        try {
            Class.forName("java.lang." + simpleName, false,
                    ClassLoader.getSystemClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * Checks whether the body text contains a fully-qualified name ending
     * with {@code .simpleName} that can be loaded as a JDK class via
     * {@code Class.forName}. This covers cases where the user writes the
     * fully-qualified name directly in the body, e.g.
     * {@code javax.swing.Timer} or {@code java.awt.EventQueue}.
     *
     * <p>The search is performed on code-only text (string literals and
     * comments blanked out) to avoid false positives.
     *
     * @param bodyText  the raw method body text
     * @param simpleName the simple type name to search for
     * @return {@code true} if a loadable FQN was found
     */
    private boolean isFullyQualifiedJdkType(String bodyText, String simpleName) {
        String codeOnly = blankNonCode(bodyText);
        // Match patterns like "pkg.subpkg.SimpleName" where the package
        // segments start with lowercase. The last segment must exactly match
        // the simple name we are looking for.
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "\\b([a-z][a-zA-Z0-9_]*(?:\\.[a-z][a-zA-Z0-9_]*)*)\\."
            + java.util.regex.Pattern.quote(simpleName) + "\\b");
        java.util.regex.Matcher m = p.matcher(codeOnly);
        while (m.find()) {
            String fqn = m.group(1) + "." + simpleName;
            try {
                Class.forName(fqn, false, ClassLoader.getSystemClassLoader());
                return true;
            } catch (ClassNotFoundException | LinkageError e) {
                // try next match
            }
        }
        return false;
    }

    /**
     * Returns a copy of {@code text} with string literals, char literals,
     * line comments ({@code //...}), and block comments ({@code /* ... *{@literal /})
     * replaced by spaces, preserving length and line structure so that
     * character offsets and word boundaries are unaffected. Used to scan only
     * real code for type-name candidates.
     */
    private static String blankNonCode(String text) {
        if (text == null) return null;
        char[] out = text.toCharArray();
        int i = 0, n = out.length;
        while (i < n) {
            char c = out[i];
            // Line comment
            if (c == '/' && i + 1 < n && out[i + 1] == '/') {
                while (i < n && out[i] != '\n') { out[i] = ' '; i++; }
                continue;
            }
            // Block comment
            if (c == '/' && i + 1 < n && out[i + 1] == '*') {
                out[i] = ' '; out[i + 1] = ' '; i += 2;
                while (i < n) {
                    if (out[i] == '*' && i + 1 < n && out[i + 1] == '/') {
                        out[i] = ' '; out[i + 1] = ' '; i += 2;
                        break;
                    }
                    if (out[i] != '\n') out[i] = ' ';
                    i++;
                }
                continue;
            }
            // String literal
            if (c == '"') {
                out[i] = ' '; i++;
                while (i < n) {
                    char sc = out[i];
                    if (sc == '\\' && i + 1 < n) { out[i] = ' '; out[i + 1] = ' '; i += 2; continue; }
                    if (sc == '"') { out[i] = ' '; i++; break; }
                    if (sc != '\n') out[i] = ' ';
                    i++;
                }
                continue;
            }
            // Char literal
            if (c == '\'') {
                out[i] = ' '; i++;
                while (i < n) {
                    char cc = out[i];
                    if (cc == '\\' && i + 1 < n) { out[i] = ' '; out[i + 1] = ' '; i += 2; continue; }
                    if (cc == '\'') { out[i] = ' '; i++; break; }
                    if (cc != '\n') out[i] = ' ';
                    i++;
                }
                continue;
            }
            i++;
        }
        return new String(out);
    }

    private List<String> findInProject(ProjectEntry entry, String simpleName) {
        List<String> matches = new ArrayList<>();
        if (entry.model() == null) return matches;
        for (CtType<?> t : entry.model().getAllTypes()) {
            if (t.getSimpleName().equals(simpleName)) {
                matches.add(t.getQualifiedName());
            }
        }
        return matches;
    }
}
