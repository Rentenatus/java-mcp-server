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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.softtek_jare.mcp.ProjectManager;
import com.softtek_jare.mcp.model.Fingerprint;
import com.softtek_jare.mcp.model.ProjectEntry;

/**
 * Tests for {@link BaseJavaTool#requireEditable}.
 *
 * @author Janusch Rentenatus
 */
class EditableGuardTest {

    @Test
    void requireEditableThrowsWhenNotEditable() {
        ProjectEntry entry = makeEntry(false);
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new DummyTool(new ProjectManager()).testRequireEditable(entry)
        );
        assertEquals("Project 'test-project' is read-only (loaded from JAR or with editable=false). "
            + "Edit tools are not available.", ex.getMessage());
    }

    @Test
    void requireEditablePassesWhenEditable() {
        ProjectEntry entry = makeEntry(true);
        assertDoesNotThrow(() -> new DummyTool(new ProjectManager()).testRequireEditable(entry));
    }

    @Test
    void requireEditableMessageContainsProjectName() {
        ProjectEntry entry = makeEntry(false);
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new DummyTool(new ProjectManager()).testRequireEditable(entry)
        );
        assert ex.getMessage().contains("test-project");
    }

    private static ProjectEntry makeEntry(boolean editable) {
        return new ProjectEntry(
            "test-project", "test-project", Instant.now().plusSeconds(600),
            Path.of("/tmp/test"), null, null, "RAW",
            false, null,
            Path.of("/tmp/test"), "test-source",
            Map.of(), false, editable
        );
    }

    /** Minimal concrete subclass to access protected method. */
    static class DummyTool extends BaseJavaTool {
        DummyTool(ProjectManager mgr) { super(mgr); }
        @Override protected String toolName() { return "dummy"; }
        @Override protected String toolDescription() { return "dummy"; }
        @Override protected Map<String, Object> toolProperties() { return Map.of(); }
        @Override protected java.util.List<String> toolRequired() { return java.util.List.of(); }
        @Override protected io.modelcontextprotocol.spec.McpSchema.CallToolResult handle(
                io.modelcontextprotocol.server.McpSyncServerExchange exchange,
                io.modelcontextprotocol.spec.McpSchema.CallToolRequest request) {
            return null;
        }
        void testRequireEditable(ProjectEntry entry) { requireEditable(entry); }
    }
}
