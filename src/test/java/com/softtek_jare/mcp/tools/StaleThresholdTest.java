package com.softtek_jare.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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
 * Tests the stale-file threshold for reference-needing operations: when few
 * files are stale (<= STALE_FILE_THRESHOLD), the caller scan re-parses them
 * on demand and the rename proceeds with a warning; when too many are stale,
 * the tool returns a MODEL_STALE domain error instead of editing.
 */
class StaleThresholdTest {

    @TempDir
    Path tempDir;
    private ProjectManager mgr;
    private EditManager editMgr;
    private RenameSymbolTool rename;
    private Path srcDir;

    @BeforeEach
    void setup() throws Exception {
        mgr = new ProjectManager();
        editMgr = new EditManager(tempDir.resolve("backups"));
        rename = new RenameSymbolTool(mgr, editMgr);
        srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
    }

    /** One stale file (well under threshold): global rename still works and
     *  the response warns that the stale file was re-parsed on demand. */
    @Test
    void globalRenameWithFewStaleFilesProceedsWithWarning() throws Exception {
        Path svc = srcDir.resolve("Service.java");
        Files.writeString(svc,
                "class Service {\n" +
                "    String getName() { return \"n\"; }\n" +
                "}\n");
        Path cli = srcDir.resolve("Client.java");
        Files.writeString(cli,
                "class Client {\n" +
                "    String fetch() {\n" +
                "        Service s = new Service();\n" +
                "        return s.getName();\n" +
                "    }\n" +
                "}\n");
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        // Touch Client.java via EditManager so it becomes the single dirty file.
        ProjectEntry touched = editMgr.writeFile(entry, cli, Files.readString(cli) + "\n// touched\n", null);
        mgr.updateEntry(touched);
        entry = mgr.find(entry.name());
        assertTrue(entry.isFileDirty(cli), "precondition: Client.java should be dirty");
        assertFalse(entry.isFileDirty(svc), "precondition: Service.java should be clean");

        CallToolResult r = rename.handle(null, req(entry.name(), "Service", "getName", "getDisplayName", "method"));

        assertFalse(r.isError(), "rename should succeed with 1 stale file");
        // Caller in the stale file (Client.java) must be updated despite staleness
        assertTrue(Files.readString(cli).contains("getDisplayName"),
                "caller in stale Client.java should be renamed");
        // The response should carry the fresh-parse advisory
        String content = r.content().toString();
        assertTrue(content.contains("re-parsed on demand") || content.contains("stale file"),
                "response should warn about re-parsed stale file");
        mgr.remove(entry.name());
    }

    /** More stale files than the threshold: global rename is refused with a
     *  MODEL_STALE domain error and no file is modified. */
    @Test
    void globalRenameWithTooManyStaleFilesReturnsDomainError() throws Exception {
        // Create 21 source files; the threshold is 20.
        Path svc = srcDir.resolve("Service.java");
        Files.writeString(svc,
                "class Service {\n" +
                "    String getName() { return \"n\"; }\n" +
                "}\n");
        for (int i = 0; i < 20; i++) {
            Files.writeString(srcDir.resolve("File" + i + ".java"),
                    "class File" + i + " { int v = " + i + "; }\n");
        }
        ProjectEntry entry = mgr.load(srcDir.toString(), null, null, true, true);

        // markDirty marks ALL source files stale (simulating a disruptive op).
        mgr.markDirty(entry.name());
        entry = mgr.find(entry.name());
        assertTrue(entry.dirtyFiles().size() > 20,
                "precondition: more than 20 files should be dirty, got " + entry.dirtyFiles().size());

        String svcBefore = Files.readString(svc);
        CallToolResult r = rename.handle(null, req(entry.name(), "Service", "getName", "getDisplayName", "method"));

        // Domain error: isError=false, but payload signals isDomainError MODEL_STALE
        assertFalse(r.isError(), "MODEL_STALE is a domain error, not a protocol error");
        String content = r.content().toString();
        assertTrue(content.contains("MODEL_STALE") || content.contains("too many"),
                "response should report MODEL_STALE");
        // No file should have been modified
        assertFalse(Files.readString(svc).contains("getDisplayName"),
                "Service.java must not be modified when blocked by stale threshold");
        mgr.remove(entry.name());
    }

    private static CallToolRequest req(String name, String className,
            String oldName, String newName, String scope) {
        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("className", className);
        args.put("oldName", oldName);
        args.put("newName", newName);
        args.put("scope", scope);
        return new CallToolRequest("rename_symbol", args);
    }
}
