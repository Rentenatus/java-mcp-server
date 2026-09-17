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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.softtek_jare.mcp.ProjectManager;
import com.softtek_jare.mcp.model.ProjectEntry;

/**
 * Tests for {@link TypeErasureChecker}.
 *
 * @author Janusch Rentenatus
 */
class TypeErasureCheckerTest {

    @TempDir
    Path tempDir;

    @Test
    void noClashWhenDifferentName() throws Exception {
        var entry = loadClass("class X { void foo(int x) {} }");
        var type = entry.model().getAllTypes().stream().findFirst().orElseThrow();
        var checker = new TypeErasureChecker();
        var result = checker.checkErasure(type, "bar", List.of("int"));
        assertFalse(result.clash());
    }

    @Test
    void noClashWhenDifferentParams() throws Exception {
        var entry = loadClass("class X { void foo(int x) {} }");
        var type = entry.model().getAllTypes().stream().findFirst().orElseThrow();
        var checker = new TypeErasureChecker();
        var result = checker.checkErasure(type, "foo", List.of("String"));
        assertFalse(result.clash());
    }

    @Test
    void clashWhenSameErasedSignature() throws Exception {
        var entry = loadClass("class X { void foo(int x) {} }");
        var type = entry.model().getAllTypes().stream().findFirst().orElseThrow();
        var checker = new TypeErasureChecker();
        var result = checker.checkErasure(type, "foo", List.of("int"));
        assertTrue(result.clash());
        assertTrue(result.message().contains("Type erasure clash"));
    }

    @Test
    void clashMessageContainsMethodNames() throws Exception {
        var entry = loadClass("class X { void foo(String x) {} }");
        var type = entry.model().getAllTypes().stream().findFirst().orElseThrow();
        var checker = new TypeErasureChecker();
        var result = checker.checkErasure(type, "foo", List.of("String"));
        assertTrue(result.clash());
        assertTrue(result.message().contains("foo"));
    }

    private ProjectEntry loadClass(String source) throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("X.java"), source);
        ProjectManager mgr = new ProjectManager();
        return mgr.load(src.toString(), null, null, true, true);
    }
}
