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
import com.softtek_jare.mcp.tools.BaseJavaTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages file-write operations with optimistic locking, fingerprint re-set,
 * backup-on-first-edit, and line-ending preservation.
 *
 * <p>This is the central write pipeline for all edit tools. It enforces the
 * safety guarantees from the edit-tools concept Section 7.
 *
 * @author Janusch Rentenatus
 */
public class EditManager {

    private static final Logger LOG = LoggerFactory.getLogger(EditManager.class);

    private final Path backupDir;
    private final Map<String, Boolean> backedUp = new HashMap<>();
    private boolean inTransaction = false;
    private final Map<Path, String> pendingChanges = new HashMap<>();
    private final List<String> editLog = new ArrayList<>();
    private Set<Path> lastCommittedFiles = Set.of();

    /**
     * Creates an EditManager with backup directory under the user home.
     */
    public EditManager() {
        this(Path.of(System.getProperty("user.home"), ".java-mcp-server", "backups"));
    }

    /**
     * Creates an EditManager with a custom backup directory (for testing).
     */
    public EditManager(Path backupDir) {
        this.backupDir = backupDir;
    }

    /**
     * Writes content to a file with the full safety pipeline:
     * <ol>
     *   <li>Backup on first edit (copies all source files)</li>
     *   <li>Optimistic lock check (fingerprint validation)</li>
     *   <li>Detect and preserve existing line-ending convention</li>
     *   <li>Write to temp file, then atomic rename</li>
     *   <li>Mark model as dirty (fingerprints stay at load-time for dirty detection)</li>
     * </ol>
     *
     * @param entry              the loaded project entry
     * @param file               the target file on disk
     * @param newContent         the new file content (may use any line ending)
     * @param expectedFingerprint optional fingerprint; null means not provided
     * @return the updated project entry with modelDirty=true
     * @throws IOException if the write fails
     * @throws IllegalArgumentException if the optimistic lock check fails
     */
    public ProjectEntry writeFile(ProjectEntry entry, Path file, String newContent, String expectedFingerprint)
            throws IOException {
        // 1. Backup on first edit
        backupOnFirstEdit(entry);

        // 2. Optimistic lock check
        BaseJavaTool.validateFingerprint(entry, file, expectedFingerprint);

        // If in transaction, accumulate changes in memory
        if (inTransaction) {
            pendingChanges.put(file.normalize(), newContent);
            LOG.info("writeFile: DEFERRED (transaction active) — {} ({} bytes buffered in memory)", file.normalize(), newContent.length());
            return entry; // no fingerprint update until commit
        }

        // 3. Detect existing line ending and preserve it
        String existingContent = Files.exists(file) ? Files.readString(file) : "";
        String lineEnding = LineEndings.detectLineEnding(existingContent);
        String adaptedContent = LineEndings.preserveOnWrite(newContent, lineEnding);

        // 4. Write to temp file, then atomic rename
        Path tempFile = file.resolveSibling(file.getFileName() + ".mcp-tmp");
        try {
            Files.writeString(tempFile, adaptedContent);
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOG.info("writeFile: IMMEDIATE write - {} ({} bytes, lineEnding={})", file, adaptedContent.length(), lineEnding.equals("\r\n") ? "CRLF" : "LF");
        } catch (IOException e) {
            // Cleanup temp file on failure
            Files.deleteIfExists(tempFile);
            throw e;
        }

        // 5. Mark model as dirty and track the edited file for per-file fingerprint skipping
        Set<Path> updatedEdited = new java.util.HashSet<>(entry.editedFiles());
        updatedEdited.add(file.normalize());
        return new ProjectEntry(
                entry.name(), entry.alias(), entry.expiryDate(),
                entry.projectDir(), entry.launcher(), entry.model(),
                entry.buildType(), entry.delomboked(), entry.lombokVersion(),
                entry.originalProjectDir(), entry.originalSource(),
                entry.sourceFingerprints(), entry.expired(), entry.editable(),
                entry.modulesDetected(), entry.modulesLoaded(), true, java.util.Collections.unmodifiableSet(updatedEdited));
    }

    /**
     * Copies all source files to the backup directory before the first edit.
     * This runs automatically — no agent request needed.
     */
    private void backupOnFirstEdit(ProjectEntry entry) {
        if (backedUp.getOrDefault(entry.name(), false)) return;
        if (entry.originalProjectDir() == null || !Files.isDirectory(entry.originalProjectDir())) return;

        Path projectBackup = backupDir.resolve(
                entry.name() + "-" + System.currentTimeMillis());
        try {
            Files.createDirectories(projectBackup);
            try (var stream = Files.walk(entry.originalProjectDir())) {
                stream.filter(Files::isRegularFile)
                      .filter(p -> p.toString().endsWith(".java"))
                      .forEach(p -> {
                          try {
                              Path rel = entry.originalProjectDir().relativize(p);
                              Path dest = projectBackup.resolve(rel);
                              Files.createDirectories(dest.getParent());
                              Files.copy(p, dest);
                          } catch (IOException e) {
                              LOG.warn("Backup failed for {}: {}", p, e.getMessage());
                          }
                      });
            }
            LOG.info("Backup created at {} for project '{}'", projectBackup, entry.name());
        } catch (IOException e) {
            LOG.warn("Backup directory creation failed: {}", e.getMessage());
        }
        backedUp.put(entry.name(), true);
    }

    /**
     * Resets the backup tracking for a project (called on reload).
     */
    public void resetBackup(String projectName) {
        backedUp.remove(projectName);
    }

    // --- Transaction Layer ---

    public void beginTransaction() {
        inTransaction = true;
        pendingChanges.clear();
    }

    public boolean isInTransaction() {
        return inTransaction;
    }

    /**
     * Commits all pending changes atomically: writes to temp files, then renames.
     * On any failure, all temp files are cleaned up and the transaction remains open.
     */
    public boolean commitTransaction() throws IOException {
        if (!inTransaction) return true;
        // Write all pending files to temp, then rename all
        List<Path> tempFiles = new ArrayList<>();
        Map<Path, String> originalContents = new HashMap<>(); // null = new file
        List<Path> renamedFiles = new ArrayList<>();
        try {
            for (var entry : pendingChanges.entrySet()) {
                Path file = entry.getKey();
                Path temp = file.resolveSibling(file.getFileName() + ".mcp-tmp");
                // Preserve existing line ending
                boolean isNew = !Files.exists(file);
                originalContents.put(file, isNew ? null : Files.readString(file));
                String existingContent = isNew ? "" : originalContents.get(file);
                String lineEnding = LineEndings.detectLineEnding(existingContent);
                String adaptedContent = LineEndings.preserveOnWrite(entry.getValue(), lineEnding);
                Files.writeString(temp, adaptedContent);
                tempFiles.add(temp);
            }
            // All temp files written successfully — now rename all
            for (var entry : pendingChanges.entrySet()) {
                Path file = entry.getKey();
                Path temp = file.resolveSibling(file.getFileName() + ".mcp-tmp");
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                renamedFiles.add(file);
            }
        } catch (IOException e) {
            // Restore already-renamed files to their original content
            for (Path file : renamedFiles) {
                String original = originalContents.get(file);
                try {
                    if (original != null) {
                        Files.writeString(file, original);
                    } else {
                        Files.deleteIfExists(file); // was a new file — undo creation
                    }
                } catch (IOException restoreEx) {
                    LOG.warn("Rollback failed for {}: {}", file, restoreEx.getMessage());
                }
            }
            // Cleanup remaining temp files (those not yet renamed)
            for (Path temp : tempFiles) {
                try { Files.deleteIfExists(temp); } catch (IOException delEx) { /* best effort */ }
            }
            // Transaction remains open
            return false;
        }
        Set<Path> committedFiles = new HashSet<>(pendingChanges.keySet());
        LOG.info("commitTransaction: {} file(s) committed atomically: {}", committedFiles.size(), committedFiles);
        pendingChanges.clear();
        inTransaction = false;
        lastCommittedFiles = java.util.Collections.unmodifiableSet(new java.util.HashSet<>(committedFiles));
        return true;
    }

    /**
     * Returns the set of files written by the last successful commitTransaction.
     * The caller (e.g. TransactionTools.Commit) uses this to update project
     * entries so that subsequent edits skip fingerprint validation for these
     * files — without this, the next edit to a committed file would fail with
     * a "modified since project load" error.
     */
    public Set<Path> getLastCommittedFiles() {
        return lastCommittedFiles;
    }

    public void rollbackTransaction() {
        LOG.info("rollbackTransaction: {} pending change(s) discarded", pendingChanges.size());
        pendingChanges.clear();
        inTransaction = false;
    }

    // --- Edit Summary ---

    public void logEdit(String description) {
        editLog.add(description);
    }

    public List<String> getEditLog() {
        return new ArrayList<>(editLog);
    }

    public int getPendingChangeCount() {
        return pendingChanges.size();
    }
}
