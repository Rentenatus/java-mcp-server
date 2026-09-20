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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.softtek_jare.mcp.ProjectManager;
import com.softtek_jare.mcp.model.ProjectEntry;

import spoon.reflect.declaration.CtType;

/**
 * Tests for {@link AutoImportResolver}.
 *
 * @author Janusch Rentenatus
 */
class AutoImportResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesProjectTypeAndAddsImport() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Service.java"), """
            class Service {
                String getName() { return "Service"; }
            }
            """);
        Files.writeString(src.resolve("Client.java"), """
            class Client {
                String fetch() { return "Client"; }
            }
            """);

        ProjectManager mgr = new ProjectManager();
        ProjectEntry entry = mgr.load(src.toString(), null, null, true, true);

        CtType<?> clientType = entry.model().getAllTypes().stream()
                .filter(t -> t.getSimpleName().equals("Client"))
                .findFirst().orElseThrow();

        AutoImportResolver resolver = new AutoImportResolver();
        var result = resolver.resolve(entry, clientType, "return new Service().getName();");

        assertTrue(result.importsToAdd().contains("Service"));
        assertTrue(result.unresolvedTypes().isEmpty());
        mgr.remove(entry.name());
    }

    @Test
    void javaLangTypesNotAdded() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("X.java"), "class X { String s; }");

        ProjectManager mgr = new ProjectManager();
        ProjectEntry entry = mgr.load(src.toString(), null, null, true, true);
        CtType<?> type = entry.model().getAllTypes().stream().findFirst().orElseThrow();

        AutoImportResolver resolver = new AutoImportResolver();
        var result = resolver.resolve(entry, type, "String s = Integer.toString(42);");

        assertFalse(result.importsToAdd().contains("String"));
        assertFalse(result.importsToAdd().contains("Integer"));
        mgr.remove(entry.name());
    }

    @Test
    void unresolvedTypeReported() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("X.java"), "class X {}");

        ProjectManager mgr = new ProjectManager();
        ProjectEntry entry = mgr.load(src.toString(), null, null, true, true);
        CtType<?> type = entry.model().getAllTypes().stream().findFirst().orElseThrow();

        AutoImportResolver resolver = new AutoImportResolver();
        var result = resolver.resolve(entry, type, "LocalDateTime now = LocalDateTime.now();");

        assertTrue(result.unresolvedTypes().contains("LocalDateTime"));
        mgr.remove(entry.name());
    }

    @Test
    void capitalizedWordsInStringsAndCommentsAreIgnored() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("X.java"), "class X {}\n");

        ProjectManager mgr = new ProjectManager();
        ProjectEntry entry = mgr.load(src.toString(), null, null, true, true);
        CtType<?> type = entry.model().getAllTypes().stream().findFirst().orElseThrow();

        AutoImportResolver resolver = new AutoImportResolver();
        // "Failed" is inside a string literal; "TODO" and "Review" are in comments.
        // None of these are real types, so they must not be reported as unresolved.
        var result = resolver.resolve(entry, type,
                "throw new RuntimeException(\"Failed\"); // TODO: Review this");

        assertTrue(result.unresolvedTypes().isEmpty(),
                "string/comment words should not be resolved as types, got: " + result.unresolvedTypes());
        mgr.remove(entry.name());
    }

    @Test
    void samePackageTypeNotAdded() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("A.java"), "class A { }");
        Files.writeString(src.resolve("B.java"), "class B { A getA() { return new A(); } }");

        ProjectManager mgr = new ProjectManager();
        ProjectEntry entry = mgr.load(src.toString(), null, null, true, true);

        CtType<?> bType = entry.model().getAllTypes().stream()
                .filter(t -> t.getSimpleName().equals("B"))
                .findFirst().orElseThrow();

        AutoImportResolver resolver = new AutoImportResolver();
        var result = resolver.resolve(entry, bType, "return new A();");

        // A is in the same (default) package — no import needed
        assertFalse(result.importsToAdd().contains("A"));
        mgr.remove(entry.name());
    }

    // --- Bug fix tests: static constant access, JDK types, java.lang exceptions ---

    @Test
    void staticConstantAccessNotMistakenForType() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("X.java"), "class X {}\n");

        ProjectManager mgr = new ProjectManager();
        ProjectEntry entry = mgr.load(src.toString(), null, null, true, true);
        CtType<?> type = entry.model().getAllTypes().stream().findFirst().orElseThrow();

        AutoImportResolver resolver = new AutoImportResolver();
        // Color.WHITE — WHITE is a static constant, not a type.
        // BorderLayout.CENTER — CENTER is a static constant, not a type.
        var result = resolver.resolve(entry, type,
                "java.awt.Color c = java.awt.Color.WHITE;");
        // WHITE must not appear in unresolved; Color is a JDK FQN so it's fine too
        assertTrue(result.unresolvedTypes().isEmpty(),
                "static constants should not be unresolved, got: " + result.unresolvedTypes());
        mgr.remove(entry.name());
    }

    @Test
    void javaLangExceptionNotInHardcodedListResolved() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("X.java"), "class X {}\n");

        ProjectManager mgr = new ProjectManager();
        ProjectEntry entry = mgr.load(src.toString(), null, null, true, true);
        CtType<?> type = entry.model().getAllTypes().stream().findFirst().orElseThrow();

        AutoImportResolver resolver = new AutoImportResolver();
        // InterruptedException is in java.lang but not in the hardcoded list.
        // It should be resolved via Class.forName("java.lang.InterruptedException").
        var result = resolver.resolve(entry, type,
                "try { Thread.sleep(100); } catch (InterruptedException ex) { }");
        assertTrue(result.unresolvedTypes().isEmpty(),
                "InterruptedException should be resolved as java.lang, got: " + result.unresolvedTypes());
        mgr.remove(entry.name());
    }

    @Test
    void jdkTypeViaFullyQualifiedNameResolved() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("X.java"), "class X {}\n");

        ProjectManager mgr = new ProjectManager();
        ProjectEntry entry = mgr.load(src.toString(), null, null, true, true);
        CtType<?> type = entry.model().getAllTypes().stream().findFirst().orElseThrow();

        AutoImportResolver resolver = new AutoImportResolver();
        // javax.swing.Timer is a JDK type referenced via FQN in the body.
        // Timer should be resolved via Class.forName("javax.swing.Timer").
        var result = resolver.resolve(entry, type,
                "javax.swing.Timer t = new javax.swing.Timer(200, null);");
        assertTrue(result.unresolvedTypes().isEmpty(),
                "javax.swing.Timer should be resolved as JDK type, got: " + result.unresolvedTypes());
        mgr.remove(entry.name());
    }

    @Test
    void nonExistentTypeStillReportedAsUnresolved() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("X.java"), "class X {}\n");

        ProjectManager mgr = new ProjectManager();
        ProjectEntry entry = mgr.load(src.toString(), null, null, true, true);
        CtType<?> type = entry.model().getAllTypes().stream().findFirst().orElseThrow();

        AutoImportResolver resolver = new AutoImportResolver();
        // NonExistentType is not a project type, not in java.lang, and no FQN
        // in the body that Class.forName can resolve — must stay unresolved.
        var result = resolver.resolve(entry, type,
                "NonExistentType x = new NonExistentType();");
        assertTrue(result.unresolvedTypes().contains("NonExistentType"),
                "non-existent type should be unresolved, got: " + result.unresolvedTypes());
        mgr.remove(entry.name());
    }

    @Test
    void manualImportDisambiguatesSameSimpleName() throws Exception {
        // "List" is ambiguous between java.util.List and java.awt.List. With a
        // manual import of java.util.List, the resolver should accept it and
        // add the import, without leaving the type unresolved.
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("X.java"), "class X {}\n");

        ProjectManager mgr = new ProjectManager();
        ProjectEntry entry = mgr.load(src.toString(), null, null, true, true);
        CtType<?> type = entry.model().getAllTypes().stream().findFirst().orElseThrow();

        AutoImportResolver resolver = new AutoImportResolver();
        var result = resolver.resolve(entry, type,
                "List<String> items;",
                java.util.List.of("java.util.List"));

        assertTrue(result.importsToAdd().contains("java.util.List"),
                "manual FQN should be added to imports, got: " + result.importsToAdd());
        assertTrue(result.unresolvedTypes().isEmpty(),
                "no unresolved types expected, got: " + result.unresolvedTypes());
        mgr.remove(entry.name());
    }

    @Test
    void manualImportInvalidFqnReportedAsUnresolved() throws Exception {
        // A manual import that is neither a project type nor a loadable JDK
        // class must be reported as unresolved, so the agent cannot inject a
        // non-existent import that would break compilation.
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("X.java"), "class X {}\n");

        ProjectManager mgr = new ProjectManager();
        ProjectEntry entry = mgr.load(src.toString(), null, null, true, true);
        CtType<?> type = entry.model().getAllTypes().stream().findFirst().orElseThrow();

        AutoImportResolver resolver = new AutoImportResolver();
        var result = resolver.resolve(entry, type,
                "List<String> items;",
                java.util.List.of("com.does.not.exist.List"));

        assertTrue(result.unresolvedTypes().stream().anyMatch(u -> u.contains("List")),
                "invalid manual FQN should be reported unresolved, got: " + result.unresolvedTypes());
        assertTrue(result.importsToAdd().isEmpty(),
                "no imports should be added for an invalid FQN, got: " + result.importsToAdd());
        mgr.remove(entry.name());
    }

    @Test
    void manualImportProjectTypeResolves() throws Exception {
        // A manual import pointing at a project type should resolve even if
        // the same simple name exists in the JDK (manual override wins).
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("X.java"), "class X {}\n");
        Files.writeString(src.resolve("Service.java"), "class Service {}\n");

        ProjectManager mgr = new ProjectManager();
        ProjectEntry entry = mgr.load(src.toString(), null, null, true, true);
        CtType<?> type = entry.model().getAllTypes().stream()
                .filter(t -> t.getSimpleName().equals("X")).findFirst().orElseThrow();

        AutoImportResolver resolver = new AutoImportResolver();
        // Reference "Service" with a manual FQN that matches the project type.
        var result = resolver.resolve(entry, type,
                "Service s = new Service();",
                java.util.List.of("Service"));

        assertTrue(result.importsToAdd().contains("Service"),
                "manual project FQN should be added, got: " + result.importsToAdd());
        assertTrue(result.unresolvedTypes().isEmpty(),
                "no unresolved types expected, got: " + result.unresolvedTypes());
        mgr.remove(entry.name());
    }
}
