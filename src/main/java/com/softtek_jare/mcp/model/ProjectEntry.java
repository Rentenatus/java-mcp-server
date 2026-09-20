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

package com.softtek_jare.mcp.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import spoon.Launcher;
import spoon.reflect.CtModel;

/**
 * The {@code ProjectEntry} record.
 *
 * @author Janusch Rentenatus
 */
public record ProjectEntry(
        String name,
        String alias,
        Instant expiryDate,
        Path projectDir,
        Launcher launcher,
        CtModel model,
        String buildType,
        boolean delomboked,
        String lombokVersion,
        Path originalProjectDir,
        String originalSource,
        Map<Path, Fingerprint> sourceFingerprints,
        boolean expired,
        boolean editable,
        int modulesDetected,
        int modulesLoaded,
        Set<Path> dirtyFiles,
        Set<Path> editedFiles
) {
    /**
     * Derived from {@link #dirtyFiles()}: {@code true} when at least one source
     * file has been edited since load and the in-memory model is stale.
     * Replaces the former {@code boolean modelDirty} component.
     */
    public boolean modelDirty() {
        return !dirtyFiles.isEmpty();
    }

    public boolean isModelDirty() {
        return !dirtyFiles.isEmpty();
    }

    public boolean isFileDirty(Path file) {
        return dirtyFiles.contains(file.normalize());
    }

    public boolean isFileEdited(Path file) {
        return editedFiles.contains(file.normalize());
    }
}
