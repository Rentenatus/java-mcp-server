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

package com.softtek_jare.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.softtek_jare.mcp.model.ProjectEntry;

/**
 * Tests for expiry management in {@link ProjectManager}: {@code markExpired},
 * {@code find} with expired entries, and {@code reload} / {@code reloadExpired}.
 *
 * @author Janusch Rentenatus
 */
class ProjectManagerExpiryTest {

    @Test
    void markExpiredMarksExpiredProjectsAndReturnsNames() throws Exception {
        ProjectManager manager = new ProjectManager();

        // Load a small project with a very short expiry (1 second)
        Path project = Paths.get("src/test/resources/no-lombok-sample").toAbsolutePath();
        ProjectEntry entry = manager.load(project.toString(), "test-expiry",
                Instant.now().plus(1, ChronoUnit.SECONDS), false);

        assertNotNull(entry);
        assertFalse(entry.expired());

        // Wait for expiry
        try { Thread.sleep(1200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        List<String> expired = manager.markExpired();
        assertTrue(expired.contains("test-expiry") || expired.contains(entry.name()),
                "Expired list should contain the project name. Got: " + expired);

        // Verify the entry is still in memory but marked as expired
        ProjectEntry found = manager.find("test-expiry");
        assertNotNull(found, "Expired project must remain in memory");
        assertTrue(found.expired(), "Project should be marked as expired");

        // Cleanup
        manager.remove("test-expiry");
    }

    @Test
    void markExpiredDoesNotReturnAlreadyExpiredProjects() throws Exception {
        ProjectManager manager = new ProjectManager();

        Path project = Paths.get("src/test/resources/no-lombok-sample").toAbsolutePath();
        manager.load(project.toString(), "test-already-expired",
                Instant.now().plus(1, ChronoUnit.SECONDS), false);

        try { Thread.sleep(1200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // First call should mark it
        List<String> firstCall = manager.markExpired();
        assertFalse(firstCall.isEmpty());

        // Second call should NOT return it again (already marked)
        List<String> secondCall = manager.markExpired();
        assertTrue(secondCall.isEmpty(),
                "Already-expired projects should not be returned again. Got: " + secondCall);

        manager.remove("test-already-expired");
    }

    @Test
    void findReturnsExpiredEntry() throws Exception {
        ProjectManager manager = new ProjectManager();

        Path project = Paths.get("src/test/resources/no-lombok-sample").toAbsolutePath();
        manager.load(project.toString(), "test-find-expired",
                Instant.now().plus(1, ChronoUnit.SECONDS), false);

        try { Thread.sleep(1200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        manager.markExpired();

        // find() must still return the expired entry
        ProjectEntry found = manager.find("test-find-expired");
        assertNotNull(found, "find() must return expired entries, not null");
        assertTrue(found.expired());

        manager.remove("test-find-expired");
    }

    @Test
    void listIncludesExpiredProjects() throws Exception {
        ProjectManager manager = new ProjectManager();

        Path project = Paths.get("src/test/resources/no-lombok-sample").toAbsolutePath();
        manager.load(project.toString(), "test-list-expired",
                Instant.now().plus(1, ChronoUnit.SECONDS), false);

        try { Thread.sleep(1200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        manager.markExpired();

        var entries = manager.list();
        boolean hasExpired = entries.stream().anyMatch(ProjectEntry::expired);
        assertTrue(hasExpired, "list() must include expired projects");

        manager.remove("test-list-expired");
    }

    @Test
    void reloadRefreshesExpiredProject() throws Exception {
        ProjectManager manager = new ProjectManager();

        Path project = Paths.get("src/test/resources/no-lombok-sample").toAbsolutePath();
        ProjectEntry entry = manager.load(project.toString(), "test-reload",
                Instant.now().plus(1, ChronoUnit.SECONDS), false);

        try { Thread.sleep(1200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        manager.markExpired();
        assertTrue(manager.find("test-reload").expired());

        // Reload
        ProjectEntry reloaded = manager.reload("test-reload");
        assertNotNull(reloaded);
        assertFalse(reloaded.expired(), "After reload, expired should be false");
        assertNotNull(reloaded.model(), "After reload, model must be present");

        manager.remove("test-reload");
    }

    @Test
    void reloadExpiredReloadsAllExpiredProjects() throws Exception {
        ProjectManager manager = new ProjectManager();

        Path projectA = Paths.get("src/test/resources/no-lombok-sample").toAbsolutePath();

        // Create a second minimal project in a temp directory
        Path projectB = java.nio.file.Files.createTempDirectory("test-expiry-second");
        java.nio.file.Path srcB = projectB.resolve("src/main/java/com/example");
        java.nio.file.Files.createDirectories(srcB);
        java.nio.file.Files.writeString(srcB.resolve("Second.java"), "package com.example;\npublic class Second {}\n");

        manager.load(projectA.toString(), "test-reload-expired-a",
                Instant.now().plus(1, ChronoUnit.SECONDS), false);
        manager.load(projectB.toString(), "test-reload-expired-b",
                Instant.now().plus(2, ChronoUnit.SECONDS), false);

        // Wait for both to expire
        try { Thread.sleep(2200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        manager.markExpired();

        long expiredCount = manager.list().stream().filter(ProjectEntry::expired).count();
        assertTrue(expiredCount >= 1, "At least one project should be expired. Got: " + expiredCount);

        List<ProjectEntry> reloaded = manager.reloadExpired();
        assertTrue(reloaded.size() >= 1, "At least one expired project should be reloaded. Got: " + reloaded.size());

        // None should be expired after reload
        assertEquals(0, manager.list().stream().filter(ProjectEntry::expired).count(),
                "No projects should be expired after reloadExpired()");

        manager.remove("test-reload-expired-a");
        manager.remove("test-reload-expired-b");
    }

    @Test
    void reloadExpiredReturnsEmptyWhenNoneExpired() throws Exception {
        ProjectManager manager = new ProjectManager();

        Path project = Paths.get("src/test/resources/no-lombok-sample").toAbsolutePath();
        manager.load(project.toString(), "test-no-expired",
                Instant.now().plus(10, ChronoUnit.MINUTES), false);

        List<ProjectEntry> reloaded = manager.reloadExpired();
        assertTrue(reloaded.isEmpty(), "Should return empty list when no projects are expired");

        manager.remove("test-no-expired");
    }

    @Test
    void originalSourceIsStoredForReload() throws Exception {
        ProjectManager manager = new ProjectManager();

        Path project = Paths.get("src/test/resources/no-lombok-sample").toAbsolutePath();
        String source = project.toString();
        ProjectEntry entry = manager.load(source, "test-source-stored", null, false);

        assertEquals(source, entry.originalSource(),
                "originalSource must match the source string passed to load()");

        manager.remove("test-source-stored");
    }
}
