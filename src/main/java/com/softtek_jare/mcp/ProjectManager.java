/*
 * MIT License
 *
 * Copyright (c) 2026 Alejandro Ferreira
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

import com.softtek_jare.mcp.model.ProjectEntry;
import com.softtek_jare.mcp.model.ProjectLoadException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import com.softtek_jare.mcp.model.Fingerprint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.Launcher;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;

/**
 * The {@code ProjectManager} class.
 *
 * @author Alejandro Ferreira
 * @author Janusch Rentenatus
 */
public class ProjectManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectManager.class);

    private final ConcurrentHashMap<String, ProjectEntry> entries = new ConcurrentHashMap<>();
    private final ProjectLoader projectLoader = new ProjectLoader();
    private final BuildDetector buildDetector = new BuildDetector();
    private final LombokDetector lombokDetector = new LombokDetector();
    private final LombokDelomboker lombokDelomboker = new LombokDelomboker();
    private com.softtek_jare.mcp.edit.EditManager editManager;

/**
 * Loads a Java project with automatic delombok enabled.
 */
    public ProjectEntry load(String source, String alias, Instant expiryDate) throws ProjectLoadException {
        return load(source, alias, expiryDate, true, null);
    }

/**
 * Loads a Java project, optionally running delombok if Lombok is detected.
 */
    public ProjectEntry load(String source, String alias, Instant expiryDate, boolean autoDelombok)
            throws ProjectLoadException {
        return load(source, alias, expiryDate, autoDelombok, null);
    }

/**
 * Loads a Java project with explicit editable flag and optional delombok.
 * JAR sources are hard-locked to editable=false regardless of the requested value.
 */
    public ProjectEntry load(String source, String alias, Instant expiryDate, boolean autoDelombok, Boolean editableOverride)
            throws ProjectLoadException {
        markExpired();

        if (expiryDate == null) {
            expiryDate = Instant.now().plus(10, ChronoUnit.MINUTES);
        }

        Path projectDir = projectLoader.resolveSource(source);
        BuildDetector.BuildInfo buildInfo = buildDetector.detect(projectDir);
        LOG.info("Build detected: {} ({}) for {}", buildInfo.type(), buildInfo.detail(), projectDir);

        String name = deriveName(projectDir, source);
        if (alias == null) alias = name;

        ProjectEntry old = entries.remove(name);
        if (old != null) {
            projectLoader.cleanup(old.projectDir());
            if (old.delomboked()) {
                lombokDelomboker.cleanup(old.projectDir());
            }
            if (editManager != null) {
                editManager.resetBackup(name);
            }
            LOG.info("Replaced previously loaded project '{}'", name);
        }

        boolean delomboked = false;
        String lombokVersion = null;
        Path sourceToAnalyze = projectDir;
        LombokDetector.LombokInfo lombokInfo = lombokDetector.detect(projectDir);
        boolean lombokInSources = lombokInfo.present() || lombokDetector.hasLombokInSources(projectDir);

        if (lombokInSources && autoDelombok) {
            try {
                Path delombokedDir = lombokDelomboker.delombok(projectDir, buildInfo.type());
                sourceToAnalyze = delombokedDir;
                delomboked = true;
                lombokVersion = lombokInfo.version();
                LOG.info("Lombok detected (version={}, build={}). Using delomboked source: {}",
                        lombokVersion, buildInfo.type(), delombokedDir);
            } catch (ProjectLoadException e) {
                LOG.warn("Auto-delombok failed ({}). Falling back to original source. "
                        + "Lombok-generated members will be missing from the model.", e.getMessage());
            }
        } else if (lombokInSources) {
            LOG.info("Lombok detected but auto-delombok disabled. Lombok-generated members "
                    + "will be missing from the model.");
        }

        try {
            Launcher launcher = createLauncher(sourceToAnalyze, buildInfo);
            CtModel model = launcher.buildModel();

            // Determine editable: JAR sources are hard-locked to false
            boolean isJar = source != null && source.toLowerCase().endsWith(".jar");
            boolean editable = isJar ? false : (editableOverride != null ? editableOverride : true);
            int modulesDetected = countModules(projectDir, buildInfo);
            int modulesLoaded = 1;

            ProjectEntry entry = new ProjectEntry(name, alias, expiryDate,
                    sourceToAnalyze, launcher, model, buildInfo.type().name(),
                    delomboked, lombokVersion,
                    projectDir, source, buildFingerprints(projectDir), false, editable,
                    modulesDetected, modulesLoaded, false, java.util.Set.of());
            entries.put(name, entry);
            LOG.info("Project '{}' loaded successfully ({} types, delomboked={})",
                    name, model.getAllTypes().size(), delomboked);
            return entry;
        } catch (Exception e) {
            if (delomboked) {
                lombokDelomboker.cleanup(sourceToAnalyze);
            }
            projectLoader.cleanup(projectDir);
            throw new ProjectLoadException("PARSE_ERROR", "Failed to parse project: " + e.getMessage());
        }
    }

    public void setEditManager(com.softtek_jare.mcp.edit.EditManager editManager) {
        this.editManager = editManager;
    }

/**
 * Updates a loaded project entry in place (used by edit tools to set modelDirty).
 */
    public void updateEntry(ProjectEntry entry) {
        entries.put(entry.name(), entry);
    }

/**
 * Marks a loaded project as dirty (model is stale after edits).
 */
    public void markDirty(String nameOrAlias) {
        ProjectEntry entry = find(nameOrAlias);
        if (entry == null) return;
        if (entry.modelDirty()) return;
        entries.put(entry.name(), new ProjectEntry(
            entry.name(), entry.alias(), entry.expiryDate(),
            entry.projectDir(), entry.launcher(), entry.model(),
            entry.buildType(), entry.delomboked(), entry.lombokVersion(),
            entry.originalProjectDir(), entry.originalSource(),
            entry.sourceFingerprints(), entry.expired(), entry.editable(),
            entry.modulesDetected(), entry.modulesLoaded(), true, entry.editedFiles()));
    }

/**
 * Marks all loaded projects as dirty.
 */
    public void markAllDirty() {
        for (var entry : new ArrayList<>(entries.values())) {
            if (!entry.modelDirty()) {
                entries.put(entry.name(), new ProjectEntry(
                    entry.name(), entry.alias(), entry.expiryDate(),
                    entry.projectDir(), entry.launcher(), entry.model(),
                    entry.buildType(), entry.delomboked(), entry.lombokVersion(),
                    entry.originalProjectDir(), entry.originalSource(),
                    entry.sourceFingerprints(), entry.expired(), entry.editable(),
                    entry.modulesDetected(), entry.modulesLoaded(), true, entry.editedFiles()));
            }
        }
    }

/**
 * Finds a loaded project by name or alias.
 */
    public ProjectEntry find(String nameOrAlias) {
        for (ProjectEntry entry : entries.values()) {
            if (entry.alias() != null && entry.alias().equals(nameOrAlias)) {
                return entry;
            }
        }
        return entries.get(nameOrAlias);
    }

/**
 * Reloads a single project from its stored original source.
 */
    public ProjectEntry reload(String nameOrAlias) throws ProjectLoadException {
        ProjectEntry old = find(nameOrAlias);
        if (old == null) {
            throw new ProjectLoadException("NOT_FOUND",
                "No project found with name or alias '" + nameOrAlias + "'. Call load_java_project first.");
        }
        return load(old.originalSource(), old.alias(), null, old.delomboked(), old.editable());
    }

/**
 * Reloads all expired projects from their stored original sources.
 */
    public List<ProjectEntry> reloadExpired() throws ProjectLoadException {
        List<ProjectEntry> reloaded = new ArrayList<>();
        for (var entry : new ArrayList<>(entries.values())) {
            if (entry.expired()) {
                reloaded.add(load(entry.originalSource(), entry.alias(), null, entry.delomboked()));
            }
        }
        return reloaded;
    }

/**
 * Removes and cleans up a loaded project.
 */
    public ProjectEntry remove(String nameOrAlias) {
        ProjectEntry entry = entries.remove(nameOrAlias);
        if (entry != null) {
            projectLoader.cleanup(entry.projectDir());
            if (entry.delomboked()) {
                lombokDelomboker.cleanup(entry.projectDir());
            }
            LOG.info("Unloaded project '{}'", nameOrAlias);
        }
        return entry;
    }

/**
 * Returns all currently loaded projects.
 */
    public Collection<ProjectEntry> list() {
        return entries.values();
    }

/**
 * Removes and cleans up ALL loaded projects. Discards every in-memory model,
 * launcher, and fingerprint. Used by the reboot_spoon tool to recover from
 * parse errors or corrupted model state without restarting the JVM.
 *
 * @return the names of the projects that were removed
 */
    public List<String> removeAll() {
        List<String> removed = new ArrayList<>();
        for (var entry : new ArrayList<>(entries.values())) {
            removed.add(entry.name());
            projectLoader.cleanup(entry.projectDir());
            if (entry.delomboked()) {
                lombokDelomboker.cleanup(entry.projectDir());
            }
            if (editManager != null) {
                editManager.resetBackup(entry.name());
            }
        }
        entries.clear();
        LOG.info("Reboot: removed {} project(s): {}", removed.size(), removed);
        return removed;
    }

/**
 * Marks all projects whose expiry date has passed as expired (without deleting them).
 * Returns the names of newly expired projects for agent notification.
 */
    public List<String> markExpired() {
        Instant now = Instant.now();
        List<String> newlyExpired = new ArrayList<>();
        for (var entry : entries.values()) {
            if (!entry.expired() && entry.expiryDate() != null && now.isAfter(entry.expiryDate())) {
                entries.put(entry.name(), new ProjectEntry(
                    entry.name(), entry.alias(), entry.expiryDate(),
                    entry.projectDir(), entry.launcher(), entry.model(),
                    entry.buildType(), entry.delomboked(), entry.lombokVersion(),
                    entry.originalProjectDir(), entry.originalSource(),
                    entry.sourceFingerprints(), true, entry.editable(),
                    entry.modulesDetected(), entry.modulesLoaded(), entry.modelDirty(), entry.editedFiles()));
                LOG.info("Project '{}' expired at {}", entry.name(), entry.expiryDate());
                newlyExpired.add(entry.name());
            }
        }
        return newlyExpired;
    }

/**
 * Builds a fingerprint map for all .java files under the given source root.
 */
    private static Map<java.nio.file.Path, Fingerprint> buildFingerprints(java.nio.file.Path sourceRoot) {
        Map<java.nio.file.Path, Fingerprint> fingerprints = new java.util.HashMap<>();
        if (sourceRoot == null || !Files.isDirectory(sourceRoot)) return fingerprints;
        try (var stream = Files.walk(sourceRoot)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".java"))
                  .forEach(p -> {
                      try {
                          long mod = Files.getLastModifiedTime(p).toMillis();
                          long size = Files.size(p);
                          fingerprints.put(p.normalize(), new Fingerprint(mod, size));
                      } catch (IOException e) {
                          LOG.warn("Could not fingerprint {}: {}", p, e.getMessage());
                      }
                  });
        } catch (IOException e) {
            LOG.warn("Could not walk source root for fingerprints: {}", e.getMessage());
        }
        return fingerprints;
    }

/**
 * Creates a Spoon Launcher for the project, using MavenLauncher for Maven projects.
 */
    public static Launcher createLauncher(Path projectDir, BuildDetector.BuildInfo buildInfo) {
        Launcher launcher;
        if (buildInfo.type() == BuildDetector.BuildType.MAVEN) {
            try {
                // Resolve symlinks in the project path and in java.home before
                // passing to MavenLauncher. On Windows (e.g. scoop-managed JDKs
                // where JAVA_HOME is a 'current' symlink), MavenLauncher's
                // system-library detection rejects symlinked paths with
                // "invalid location for system libraries". Resolving to the
                // real path avoids this.
                Path realProjectDir = resolveRealPath(projectDir);
                String realJavaHome = resolveRealJavaHome();
                String savedJavaHome = realJavaHome != null
                        ? setAndSaveJavaHome(realJavaHome) : null;
                try {
                    launcher = new MavenLauncher(realProjectDir.toAbsolutePath().toString(), MavenLauncher.SOURCE_TYPE.APP_SOURCE);
                    launcher.getEnvironment().setCommentEnabled(true);
                    /* MavenLauncher resolves JDK classpath via ECJ bootclasspath detection;
                       calling applyJdkClasspath here would break it (see P36). */
                    LOG.info("Using MavenLauncher for {} (NoClasspath={}, JDK via ECJ bootclasspath)",
                            realProjectDir, launcher.getEnvironment().getNoClasspath());
                    return launcher;
                } finally {
                    if (savedJavaHome != null) {
                        System.setProperty("java.home", savedJavaHome);
                    }
                }
            } catch (Exception e) {
                LOG.warn("MavenLauncher failed ({}), falling back to noclasspath", e.getMessage());
            }
        }

        launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        applyJdkClasspath(launcher);

        Path srcDir = findSourceDir(projectDir, buildInfo);
        if (srcDir != null && Files.isDirectory(srcDir)) {
            launcher.addInputResource(srcDir.toAbsolutePath().toString());
        } else if (buildInfo.type() == BuildDetector.BuildType.GRADLE) {
            addGradleSourceDirs(launcher, projectDir);
        } else {
            launcher.addInputResource(projectDir.toAbsolutePath().toString());
        }

        LOG.info("Using noclasspath Launcher for {} (src: {})", buildInfo.type(), srcDir);
        return launcher;
    }

/**
 * Supplements the Spoon launcher classpath with JDK module JARs
 * (java.base, java.desktop, etc.) so JDK types like javax.swing are resolvable.
 * Failures are logged and silently skipped; the launcher continues in
 * noclasspath mode if no JDK JARs are available.
 */
    private static void applyJdkClasspath(Launcher launcher) {
        try {
            List<String> jdkCp = new JdkClasspathResolver().resolve();
            if (jdkCp.isEmpty()) {
                LOG.warn("JDK classpath empty; JDK types may be unresolved (noclasspath fallback)");
                return;
            }
            List<String> combined = new ArrayList<>();
            String[] existing = launcher.getEnvironment().getSourceClasspath();
            if (existing != null) {
                for (String s : existing) {
                    combined.add(s);
                }
            }
            combined.addAll(jdkCp);
            launcher.getEnvironment().setSourceClasspath(combined.toArray(new String[0]));
            LOG.info("JDK classpath applied: {} JARs ({} total classpath entries)",
                    jdkCp.size(), combined.size());
        } catch (Exception e) {
            LOG.warn("JDK classpath resolution failed ({}); continuing without it", e.getMessage());
        }
    }

/**
 * Finds the source directory for the project based on build type.
 */
    private static Path findSourceDir(Path projectDir, BuildDetector.BuildInfo buildInfo) {
        if (buildInfo.type() == BuildDetector.BuildType.MAVEN) {
            Path src = projectDir.resolve("src/main/java");
            if (Files.isDirectory(src)) return src;
        }
        Path src = projectDir.resolve("src");
        if (Files.isDirectory(src)) return src;
        return projectDir;
    }

/**
 * Resolves the source root directory for a loaded project, used by edit tools
 * that create new files (add_class, add_package). Derives the root from an
 * existing type's on-disk position so it works regardless of build system
 * (Maven, Gradle, RAW), falling back to the {@code src/main/java} convention
 * and finally to the project directory itself.
 */
    public static Path resolveSourceRoot(ProjectEntry entry) {
        if (entry == null || entry.projectDir() == null) return null;
        if (entry.model() != null) {
            for (var t : entry.model().getAllTypes()) {
                if (t.getPosition() == null || t.getPosition().getFile() == null) continue;
                Path file = t.getPosition().getFile().toPath().normalize();
                String pkg = (t.getPackage() != null) ? t.getPackage().getQualifiedName() : "";
                int segs = pkg.isEmpty() ? 0 : (int) pkg.chars().filter(c -> c == '.').count() + 1;
                Path root = file.getParent();
                for (int i = 0; i < segs && root != null; i++) root = root.getParent();
                if (root != null && Files.isDirectory(root)) {
                    LOG.info("resolveSourceRoot: derived from {} -> {}", t.getSimpleName(), root);
                    return root;
                }
                break;
            }
        }
        Path conv = entry.projectDir().resolve("src/main/java");
        if (Files.isDirectory(conv)) {
            LOG.info("resolveSourceRoot: using convention src/main/java -> {}", conv);
            return conv;
        }
        LOG.info("resolveSourceRoot: fallback to projectDir -> {}", entry.projectDir());
        return entry.projectDir();
    }

/**
 * Resolves a path to its real (non-symlink) form. Returns the original
 * path if resolution fails (e.g. on filesystems that don't support links).
 */
    private static Path resolveRealPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path;
        }
    }

/**
 * Returns the real (non-symlink) java.home path, or null if it cannot be
 * resolved. On Windows scoop JDKs, java.home is typically a 'current'
 * symlink that MavenLauncher rejects.
 */
    private static String resolveRealJavaHome() {
        String javaHome = System.getProperty("java.home", "");
        if (javaHome.isEmpty()) return null;
        try {
            Path real = java.nio.file.Path.of(javaHome).toRealPath();
            return real.toAbsolutePath().toString();
        } catch (IOException e) {
            return null;
        }
    }

/**
 * Temporarily sets java.home to the real path and returns the previous
 * value so the caller can restore it in a finally block.
 */
    private static String setAndSaveJavaHome(String realJavaHome) {
        String old = System.getProperty("java.home");
        System.setProperty("java.home", realJavaHome);
        return old;
    }

/**
 * Adds Gradle source directories to the launcher.
 */
    private static void addGradleSourceDirs(Launcher launcher, Path projectDir) {
        String[] gradleSrcs = {"src/main/java", "src/main/kotlin", "src/main/groovy"};
        boolean found = false;
        for (String gs : gradleSrcs) {
            Path p = projectDir.resolve(gs);
            if (Files.isDirectory(p)) {
                launcher.addInputResource(p.toAbsolutePath().toString());
                found = true;
            }
        }
        if (!found) {
            launcher.addInputResource(projectDir.toAbsolutePath().toString());
        }
    }

/**
 * Derives a project name from the project directory name.
 */
    public static String deriveName(Path projectDir, String source) {
        String name = projectDir.getFileName().toString();
        return name;
    }

/**
 * Counts the number of modules in a multi-module build.
 * Maven: counts <module> entries in pom.xml. Gradle: counts include directives in settings.gradle.
 * Returns 1 for single-module projects (the project itself).
 */
    private static int countModules(Path projectDir, BuildDetector.BuildInfo buildInfo) {
        if (buildInfo.type() == BuildDetector.BuildType.MAVEN) {
            Path pom = projectDir.resolve("pom.xml");
            if (Files.exists(pom)) {
                try {
                    String content = Files.readString(pom);
                    int count = 0;
                    int idx = 0;
                    while ((idx = content.indexOf("<module>", idx)) != -1) {
                        count++;
                        idx += 8;
                    }
                    return Math.max(count, 1);
                } catch (IOException e) {
                    return 1;
                }
            }
        } else if (buildInfo.type() == BuildDetector.BuildType.GRADLE) {
            for (String settingsFile : new String[]{"settings.gradle", "settings.gradle.kts"}) {
                Path settings = projectDir.resolve(settingsFile);
                if (Files.exists(settings)) {
                    try {
                        String content = Files.readString(settings);
                        int count = 0;
                        for (String line : content.split("\\R")) {
                            String trimmed = line.trim();
                            if (trimmed.startsWith("include") || trimmed.startsWith("include(")) {
                                // count comma-separated module names in include directive
                                int start = trimmed.indexOf("'");
                                if (start < 0) start = trimmed.indexOf("\"");
                                if (start >= 0) {
                                    String rest = trimmed.substring(start);
                                    count += rest.split("['\"]").length / 2;
                                }
                                if (count == 0) count = 1; // at least one
                            }
                        }
                        return Math.max(count, 1);
                    } catch (IOException e) {
                        return 1;
                    }
                }
            }
        }
        return 1;
    }
}
