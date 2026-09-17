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
}
