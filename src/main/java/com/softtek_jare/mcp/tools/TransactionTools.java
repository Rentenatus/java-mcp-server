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

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import com.softtek_jare.mcp.ProjectManager;
import com.softtek_jare.mcp.edit.EditManager;

import java.util.List;
import java.util.Map;

/**
 * Transaction tools: begin_transaction, commit_transaction, rollback_transaction,
 * get_edit_summary.
 *
 * @author Janusch Rentenatus
 */
public class TransactionTools extends BaseJavaTool {

    private final EditManager editManager;

    public TransactionTools(ProjectManager manager, EditManager editManager) {
        super(manager);
        this.editManager = editManager;
    }

    @Override protected String toolName() {
        // This class handles multiple tools; registration uses separate instances
        return "transaction";
    }
    @Override protected String toolDescription() { return "Transaction operations"; }
    @Override protected Map<String, Object> toolProperties() { return Map.of(); }
    @Override protected List<String> toolRequired() { return List.of(); }

    @Override
    protected CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        return error("Use specific transaction tool instances");
    }

    // --- Individual tool classes ---

    public static class Begin extends BaseJavaTool {
        private final EditManager editManager;
        public Begin(ProjectManager mgr, EditManager em) { super(mgr); this.editManager = em; }
        @Override protected String toolName() { return "begin_transaction"; }
        @Override protected String toolDescription() {
            return "Start a transaction. Subsequent edit calls accumulate in memory until commit.";
        }
        @Override protected Map<String, Object> toolProperties() { return Map.of(); }
        @Override protected List<String> toolRequired() { return List.of(); }
        @Override protected CallToolResult handle(McpSyncServerExchange ex, CallToolRequest req) {
            editManager.beginTransaction();
            return ok("Transaction started. Edits will accumulate in memory until commit_transaction.");
        }
    }

    public static class Commit extends BaseJavaTool {
        private final EditManager editManager;
        public Commit(ProjectManager mgr, EditManager em) { super(mgr); this.editManager = em; }
        @Override protected String toolName() { return "commit_transaction"; }
        @Override protected String toolDescription() {
            return "Write all accumulated changes to disk atomically. Either all succeed or none.";
        }
        @Override protected Map<String, Object> toolProperties() { return Map.of(); }
        @Override protected List<String> toolRequired() { return List.of(); }
        @Override protected CallToolResult handle(McpSyncServerExchange ex, CallToolRequest req) throws Exception {
            if (!editManager.isInTransaction()) {
                return error("No active transaction. Call begin_transaction first.");
            }
            int pending = editManager.getPendingChangeCount();
            boolean success = editManager.commitTransaction();
            if (success) {
                manager.markAllDirty();
                return ok("Transaction committed. " + pending + " file(s) written atomically.");
            } else {
                return error("Commit failed. All temp files cleaned up. Transaction is still open — "
                        + "fix the issue and retry, or call rollback_transaction.");
            }
        }
    }

    public static class Rollback extends BaseJavaTool {
        private final EditManager editManager;
        public Rollback(ProjectManager mgr, EditManager em) { super(mgr); this.editManager = em; }
        @Override protected String toolName() { return "rollback_transaction"; }
        @Override protected String toolDescription() {
            return "Discard all accumulated changes. Original fingerprints remain valid.";
        }
        @Override protected Map<String, Object> toolProperties() { return Map.of(); }
        @Override protected List<String> toolRequired() { return List.of(); }
        @Override protected CallToolResult handle(McpSyncServerExchange ex, CallToolRequest req) {
            if (!editManager.isInTransaction()) {
                return error("No active transaction to roll back.");
            }
            editManager.rollbackTransaction();
            return ok("Transaction rolled back. All pending changes discarded.");
        }
    }

    public static class GetEditSummary extends BaseJavaTool {
        private final EditManager editManager;
        public GetEditSummary(ProjectManager mgr, EditManager em) { super(mgr); this.editManager = em; }
        @Override protected String toolName() { return "get_edit_summary"; }
        @Override protected String toolDescription() {
            return "Returns a compact summary of everything changed since the project was loaded.";
        }
        @Override protected Map<String, Object> toolProperties() { return Map.of(); }
        @Override protected List<String> toolRequired() { return List.of(); }
        @Override protected CallToolResult handle(McpSyncServerExchange ex, CallToolRequest req) {
            List<String> log = editManager.getEditLog();
            if (log.isEmpty()) {
                return ok("No edits recorded since project load.");
            }
            StringBuilder sb = new StringBuilder("Edit summary (since load):\n");
            for (String entry : log) {
                sb.append("- ").append(entry).append("\n");
            }
            return ok(sb);
        }
    }
}
