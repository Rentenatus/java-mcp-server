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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.softtek_jare.mcp.ProjectManager;
import com.softtek_jare.mcp.edit.EditManager;
import com.softtek_jare.mcp.model.ProjectEntry;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

/**
 * Tests for {@link RenameSymbolTool}.
 *
 * @author Janusch Rentenatus
 */
class RenameSymbolToolTest {

    @TempDir
    Path tempDir;
    private ProjectManager mgr;
    private EditManager editMgr;
    private RenameSymbolTool tool;
    private Path srcDir;

    @BeforeEach
    void setup() throws Exception {
        mgr = new ProjectManager();
        editMgr = new EditManager(tempDir.resolve("backups"));
        tool = new RenameSymbolTool(mgr, editMgr);
        srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
    }

    @Test
    void renameMethodUpdatesDeclarationAndCallers() throws Exception {
        Path file = srcDir.resolve("Service.java");
        Files.writeString(file, """
            class Service {
                String getName() {
                    return "Service";
                }
                String getFullName() {
                    return getName() + " extra";
                }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, mockRequest(
                entry.name(), "Service", "getName", "getDisplayName", "method"));

        assertFalse(result.isError());
        String written = Files.readString(file);
        assertTrue(written.contains("getDisplayName"));
        assertTrue(!written.contains("getName"));
        mgr.remove(entry.name());
    }

    @Test
    void renameMethodAcrossMultipleFiles() throws Exception {
        Path svcFile = srcDir.resolve("Service.java");
        Files.writeString(svcFile, """
            class Service {
                String getValue() {
                    return "val";
                }
            }
            """);
        Path cliFile = srcDir.resolve("Client.java");
        Files.writeString(cliFile, """
            class Client {
                String fetch() {
                    Service s = new Service();
                    return s.getValue();
                }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, mockRequest(
                entry.name(), "Service", "getValue", "retrieveValue", "method"));

        assertFalse(result.isError());
        assertTrue(Files.readString(svcFile).contains("retrieveValue"));
        assertTrue(Files.readString(cliFile).contains("retrieveValue"));
        mgr.remove(entry.name());
    }

    @Test
    void renameFieldUpdatesDeclarationAndAccesses() throws Exception {
        Path file = srcDir.resolve("Config.java");
        Files.writeString(file, """
            class Config {
                int timeout = 5000;
                int getTimeout() {
                    return timeout;
                }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, mockRequest(
                entry.name(), "Config", "timeout", "timeoutMillis", "field"));

        assertFalse(result.isError());
        String written = Files.readString(file);
        assertTrue(written.contains("timeoutMillis"));
        mgr.remove(entry.name());
    }

    @Test
    void unresolvedReferencesForStringLiterals() throws Exception {
        Path file = srcDir.resolve("Reflect.java");
        Files.writeString(file, """
            class Reflect {
                String lookup() {
                    return (String) Reflect.class.getMethod("getName").invoke(this);
                }
                String getName() {
                    return "Reflect";
                }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, mockRequest(
                entry.name(), "Reflect", "getName", "getDisplayName", "method"));

        assertFalse(result.isError());
        // The result should mention unresolved references
        String content = result.content().toString();
        assertTrue(content.contains("Unresolved references") || content.contains("unresolved"));
        mgr.remove(entry.name());
    }

    @Test
    void typeNotFound() throws Exception {
        Path file = srcDir.resolve("X.java");
        Files.writeString(file, "class X {}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        assertThrows(IllegalArgumentException.class, () ->
            tool.handle(null, mockRequest(
                entry.name(), "NonExistent", "foo", "bar", "method")));

        mgr.remove(entry.name());
    }

    @Test
    void renameSkipsInlineAndTrailingComments() throws Exception {
        Path file = srcDir.resolve("C.java");
        Files.writeString(file, """
            class C {
                int compute() {
                    int result = compute(); /* compute result */
                    return result; // uses compute internally
                }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult r = tool.handle(null, mockRequest(
                entry.name(), "C", "compute", "evaluate", "method"));

        assertFalse(r.isError());
        String written = Files.readString(file);
        assertTrue(written.contains("evaluate()")); // call site renamed
        // The word "compute" inside comments must remain untouched.
        assertTrue(written.contains("/* compute result */"));
        assertTrue(written.contains("// uses compute internally"));
        mgr.remove(entry.name());
    }

    @Test
    void renameOverloadedMethodDeclarationOnlyErrors() throws Exception {
        Path file = srcDir.resolve("Over.java");
        Files.writeString(file, """
            class Over {
                int process(int x) { return x; }
                String process(String s) { return s; }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult result = tool.handle(null, mockRequestDeclOnly(
                entry.name(), "Over", "process", "execute", "method"));

        assertFalse(result.isError());
        assertTrue(result.content().toString().contains("isDomainError"));
        assertTrue(result.content().toString().contains("overloaded"));
        mgr.remove(entry.name());
    }

    private static CallToolRequest mockRequest(String name, String className,
            String oldName, String newName, String scope) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("className", className);
        args.put("oldName", oldName);
        args.put("newName", newName);
        args.put("scope", scope);
        return new CallToolRequest("rename_symbol", args);
    }

    @Test
    void renameHandlesCodeAfterInlineBlockComment() throws Exception {
        // Code following an inline /* comment */ on the same line must be
        // renamed — replaceInCodeOnly must not skip the entire line.
        Path file = srcDir.resolve("Inline.java");
        Files.writeString(file, """
            class Inline {
                void run() {
                    /* setup */ compute();
                }
                int compute() { return 0; }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult r = tool.handle(null, mockRequest(
                entry.name(), "Inline", "compute", "evaluate", "method"));

        assertFalse(r.isError());
        String written = Files.readString(file);
        assertTrue(written.contains("/* setup */ evaluate()"),
                "code after inline block comment must be renamed, got:\n" + written);
        mgr.remove(entry.name());
    }

    @Test
    void renameHandlesCodeAfterBlockCommentClose() throws Exception {
        // When a multi-line block comment closes mid-line, the code after */
        // must be renamed, not skipped as part of the comment.
        Path file = srcDir.resolve("Multi.java");
        Files.writeString(file, """
            class Multi {
                void run() {
                    /* multi
                       line
                       comment */ compute();
                }
                int compute() { return 0; }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult r = tool.handle(null, mockRequest(
                entry.name(), "Multi", "compute", "evaluate", "method"));

        assertFalse(r.isError());
        String written = Files.readString(file);
        assertTrue(written.contains("*/ evaluate()"),
                "code after block comment close must be renamed, got:\n" + written);
        mgr.remove(entry.name());
    }

    private static CallToolRequest mockRequestDeclOnly(String name, String className,
            String oldName, String newName, String scope) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("className", className);
        args.put("oldName", oldName);
        args.put("newName", newName);
        args.put("scope", scope);
        args.put("updateCallers", false);
        return new CallToolRequest("rename_symbol", args);
    }

    @Test
    void renameInterfaceMethodAlsoRenamesOverrideImplementations() throws Exception {
        // Renaming a method declared in an interface must also rename every
        // @Override implementation in subtypes, otherwise the code no longer
        // compiles (Override no longer overrides anything).
        Path iface = srcDir.resolve("Shape.java");
        Files.writeString(iface, """
            interface Shape {
                String getMarker();
            }
            """);
        Path circle = srcDir.resolve("Circle.java");
        Files.writeString(circle, """
            class Circle implements Shape {
                @Override
                public String getMarker() {
                    return "C";
                }
            }
            """);
        Path square = srcDir.resolve("Square.java");
        Files.writeString(square, """
            class Square implements Shape {
                @Override
                public String getMarker() {
                    return "S";
                }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult r = tool.handle(null, mockRequest(
                entry.name(), "Shape", "getMarker", "getMarkerText", "method"));

        assertFalse(r.isError());
        // Interface declaration renamed
        assertTrue(Files.readString(iface).contains("getMarkerText"));
        assertTrue(!Files.readString(iface).contains("getMarker()"));
        // Both override implementations renamed
        String circleWritten = Files.readString(circle);
        assertTrue(circleWritten.contains("getMarkerText"),
                "Circle override must be renamed, got:\n" + circleWritten);
        assertTrue(!circleWritten.contains("getMarker()"),
                "Circle must not retain old method name, got:\n" + circleWritten);
        String squareWritten = Files.readString(square);
        assertTrue(squareWritten.contains("getMarkerText"),
                "Square override must be renamed, got:\n" + squareWritten);
        assertTrue(!squareWritten.contains("getMarker()"),
                "Square must not retain old method name, got:\n" + squareWritten);
        mgr.remove(entry.name());
    }

    @Test
    void renameAbstractMethodAlsoRenamesOverrideImplementations() throws Exception {
        // Same as above but via an abstract class hierarchy (superclass method),
        // verifying the override scan works for class-based inheritance too.
        Path base = srcDir.resolve("Animal.java");
        Files.writeString(base, """
            abstract class Animal {
                abstract String sound();
            }
            """);
        Path dog = srcDir.resolve("Dog.java");
        Files.writeString(dog, """
            class Dog extends Animal {
                @Override
                String sound() {
                    return "woof";
                }
            }
            """);
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        CallToolResult r = tool.handle(null, mockRequest(
                entry.name(), "Animal", "sound", "makeSound", "method"));

        assertFalse(r.isError());
        assertTrue(Files.readString(base).contains("makeSound"));
        String dogWritten = Files.readString(dog);
        assertTrue(dogWritten.contains("makeSound"),
                "Dog override must be renamed, got:\n" + dogWritten);
        assertTrue(!dogWritten.contains("sound()"),
                "Dog must not retain old method name, got:\n" + dogWritten);
        mgr.remove(entry.name());
    }

    @Test
    void renameInterfaceMethodOnMastermindSampleRenamesAllOverrides() throws Exception {
        // Regression test against the real mastermind-sample game: renaming the
        // interface method Zeile.getMarker must reach all three @Override
        // implementations (CodeZeile, FeedbackZeile, GuessZeile). AbstractZeile
        // does not override getMarker and must be left untouched.
        Path sampleSrc = Path.of("src/test/resources/mastermind-sample/src/main/java/com/example");
        Path copySrc = tempDir.resolve("src/com/example");
        Files.createDirectories(copySrc);
        for (String name : new String[]{"Zeile.java", "AbstractZeile.java",
                "CodeZeile.java", "FeedbackZeile.java", "GuessZeile.java"}) {
            Files.copy(sampleSrc.resolve(name), copySrc.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        ProjectEntry entry = mgr.load(tempDir.resolve("src").toString(), null, null, true, true);

        CallToolResult r = tool.handle(null, mockRequest(
                entry.name(), "com.example.Zeile", "getMarker", "getMarkerText", "method"));

        assertFalse(r.isError());
        // Interface + all three overrides renamed
        for (String name : new String[]{"Zeile.java", "CodeZeile.java",
                "FeedbackZeile.java", "GuessZeile.java"}) {
            String written = Files.readString(copySrc.resolve(name));
            assertTrue(written.contains("getMarkerText"),
                    name + " must contain new name, got:\n" + written);
            assertTrue(!written.contains("getMarker()"),
                    name + " must not retain old method name, got:\n" + written);
        }
        // AbstractZeile has no getMarker and must be unchanged
        String absWritten = Files.readString(copySrc.resolve("AbstractZeile.java"));
        assertTrue(!absWritten.contains("getMarkerText"),
                "AbstractZeile must not be touched, got:\n" + absWritten);
        mgr.remove(entry.name());
    }
}
