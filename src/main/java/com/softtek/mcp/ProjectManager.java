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

package com.softtek.mcp;

import com.softtek.mcp.model.ProjectEntry;
import com.softtek.mcp.model.ProjectLoadException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.Launcher;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;

/**
 * The {@code ProjectManager} class.
 *
 * @author Alejandro Ferreira
 */
public class ProjectManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectManager.class);

    private final ConcurrentHashMap<String, ProjectEntry> entries = new ConcurrentHashMap<>();
    private final ProjectLoader projectLoader = new ProjectLoader();
    private final BuildDetector buildDetector = new BuildDetector();
    private final LombokDetector lombokDetector = new LombokDetector();
    private final LombokDelomboker lombokDelomboker = new LombokDelomboker();

/**
 * Loads a Java project with automatic delombok enabled.
 */
    public ProjectEntry load(String source, String alias, Instant expiryDate) throws ProjectLoadException {
        return load(source, alias, expiryDate, true);
    }

/**
 * Loads a Java project, optionally running delombok if Lombok is detected.
 */
    public ProjectEntry load(String source, String alias, Instant expiryDate, boolean autoDelombok)
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

            ProjectEntry entry = new ProjectEntry(name, alias, expiryDate,
                    sourceToAnalyze, launcher, model, buildInfo.type().name(),
                    delomboked, lombokVersion,
                    projectDir, source, Map.of(), false);
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
                    entry.sourceFingerprints(), true));
                LOG.info("Project '{}' expired at {}", entry.name(), entry.expiryDate());
                newlyExpired.add(entry.name());
            }
        }
        return newlyExpired;
    }

/**
 * Creates a Spoon Launcher for the project, using MavenLauncher for Maven projects.
 */
    public static Launcher createLauncher(Path projectDir, BuildDetector.BuildInfo buildInfo) {
        Launcher launcher;
        if (buildInfo.type() == BuildDetector.BuildType.MAVEN) {
            try {
                launcher = new MavenLauncher(projectDir.toAbsolutePath().toString(), MavenLauncher.SOURCE_TYPE.APP_SOURCE);
                launcher.getEnvironment().setCommentEnabled(true);
                LOG.info("Using MavenLauncher for {}", projectDir);
                return launcher;
            } catch (Exception e) {
                LOG.warn("MavenLauncher failed ({}), falling back to noclasspath", e.getMessage());
            }
        }

        launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);

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
}
