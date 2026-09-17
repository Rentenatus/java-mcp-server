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

import com.softtek.mcp.model.ProjectLoadException;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code ProjectLoader} class.
 *
 * @author Alejandro Ferreira
 */
public class ProjectLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectLoader.class);
    private static final Path TEMP_BASE;

    static {
        String tmp = System.getProperty("java.io.tmpdir");
        TEMP_BASE = Path.of(tmp, "java-mcp-projects");
        try { Files.createDirectories(TEMP_BASE); } catch (IOException e) { throw new RuntimeException(e); }
    }

/**
 * Resolves a source string (Git URL, local path, file:// URI, or archive) to a local project directory.
 */
    public Path resolveSource(String source) throws ProjectLoadException {
        if (source == null || source.isBlank())
            throw new ProjectLoadException("INVALID_PARAMS", "source is required");

        source = source.trim();

        if (source.startsWith("http://") || source.startsWith("https://")) {
            if (isArchiveUrl(source)) {
                return downloadAndExtract(source);
            } else {
                return gitClone(source);
            }
        }

        if (source.startsWith("file://")) {
            source = source.substring(7);
        }

        Path localPath = Path.of(source).normalize().toAbsolutePath();
        if (!Files.exists(localPath))
            throw new ProjectLoadException("FILE_NOT_FOUND", "Path not found: " + localPath);

        if (Files.isRegularFile(localPath) && isArchiveFile(localPath)) {
            return extractArchive(localPath);
        }

        if (!Files.isDirectory(localPath))
            throw new ProjectLoadException("INVALID_PARAMS", "Path is not a directory: " + localPath);

        return localPath;
    }

/**
 * Recursively deletes a temporary project directory.
 */
    public void cleanup(Path dir) {
        if (dir == null) return;
        if (!dir.startsWith(TEMP_BASE)) return;
        try {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException e) {} });
            LOG.info("Cleaned up temp dir: {}", dir);
        } catch (IOException e) {
            LOG.warn("Failed to clean up {}: {}", dir, e.getMessage());
        }
    }

/**
 * Clones a Git repository into a temporary directory.
 */
    private Path gitClone(String url) throws ProjectLoadException {
        String repoName = extractRepoName(url);
        Path dest = TEMP_BASE.resolve(repoName + "_" + System.currentTimeMillis());
        LOG.info("Cloning {} into {}...", url, dest);
        try {
            Git.cloneRepository()
                .setURI(url)
                .setDirectory(dest.toFile())
                .setCloneSubmodules(true)
                .call();
            LOG.info("Clone complete: {}", dest);
            return dest;
        } catch (Exception e) {
            throw new ProjectLoadException("GIT_ERROR", "Failed to clone " + url + ": " + e.getMessage());
        }
    }

/**
 * Downloads an archive from a URL and extracts it.
 */
    private Path downloadAndExtract(String url) throws ProjectLoadException {
        Path tmpFile = TEMP_BASE.resolve("download_" + System.currentTimeMillis() + "_" + extractFileName(url));
        LOG.info("Downloading {} ...", url);
        try {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            }
            Path extracted = extractArchive(tmpFile);
            Files.deleteIfExists(tmpFile);
            return extracted;
        } catch (IOException e) {
            try { Files.deleteIfExists(tmpFile); } catch (IOException ignored) {}
            throw new ProjectLoadException("DOWNLOAD_ERROR", "Failed to download " + url + ": " + e.getMessage());
        }
    }

/**
 * Extracts an archive (zip, tar.gz, tar) into a temporary directory.
 */
    private Path extractArchive(Path archive) throws ProjectLoadException {
        String baseName = archive.getFileName().toString();
        int dot = baseName.indexOf('.');
        String name = dot > 0 ? baseName.substring(0, dot) : baseName;
        Path dest = TEMP_BASE.resolve(name + "_" + System.currentTimeMillis());

        LOG.info("Extracting {} into {}...", archive, dest);
        try {
            Files.createDirectories(dest);
            String nameLower = archive.toString().toLowerCase();

            if (nameLower.endsWith(".zip")) {
                extractZip(archive, dest);
            } else if (nameLower.endsWith(".tar.gz") || nameLower.endsWith(".tgz")) {
                extractTarGz(archive, dest);
            } else if (nameLower.endsWith(".tar")) {
                extractTar(archive, dest);
            } else {
                throw new ProjectLoadException("INVALID_PARAMS", "Unsupported archive format: " + archive);
            }

            return findSingleRoot(dest);
        } catch (ProjectLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new ProjectLoadException("EXTRACT_ERROR", "Failed to extract " + archive + ": " + e.getMessage());
        }
    }

/**
 * Extracts a ZIP archive, guarding against zip-slip path traversal.
 */
    private void extractZip(Path zipFile, Path dest) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                Path out = dest.resolve(entry.getName()).normalize();
                if (!out.startsWith(dest)) throw new IOException("Zip slip: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(out)) {
                        int n; while ((n = zis.read(buf)) > 0) os.write(buf, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }
    }

/**
 * Extracts a gzipped tar archive.
 */
    private void extractTarGz(Path tarFile, Path dest) throws IOException {
        try (InputStream fi = Files.newInputStream(tarFile);
             InputStream gzi = new java.util.zip.GZIPInputStream(fi);
             org.apache.commons.compress.archivers.tar.TarArchiveInputStream tai =
                 new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gzi)) {
            extractTarStream(tai, dest);
        }
    }

/**
 * Extracts an uncompressed tar archive.
 */
    private void extractTar(Path tarFile, Path dest) throws IOException {
        try (InputStream fi = Files.newInputStream(tarFile);
             org.apache.commons.compress.archivers.tar.TarArchiveInputStream tai =
                 new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(fi)) {
            extractTarStream(tai, dest);
        }
    }

/**
 * Extracts entries from a tar input stream, guarding against tar-slip.
 */
    private void extractTarStream(org.apache.commons.compress.archivers.tar.TarArchiveInputStream tai, Path dest) throws IOException {
        org.apache.commons.compress.archivers.ArchiveEntry entry;
        byte[] buf = new byte[8192];
        while ((entry = tai.getNextEntry()) != null) {
            Path out = dest.resolve(entry.getName()).normalize();
            if (!out.startsWith(dest)) throw new IOException("Tar slip: " + entry.getName());
            if (entry.isDirectory()) {
                Files.createDirectories(out);
            } else {
                Files.createDirectories(out.getParent());
                try (OutputStream os = Files.newOutputStream(out)) {
                    int n; while ((n = tai.read(buf)) > 0) os.write(buf, 0, n);
                }
            }
        }
    }

/**
 * If the extracted archive contains a single root directory, returns it.
 */
    private Path findSingleRoot(Path dest) throws IOException {
        try (var list = Files.list(dest)) {
            var dirs = list.filter(Files::isDirectory).toList();
            if (dirs.size() == 1 && hasJavaFiles(dirs.get(0))) {
                return dirs.get(0);
            }
        }
        return dest;
    }

/**
 * Checks whether a directory contains any {@code .java} files within three levels.
 */
    private boolean hasJavaFiles(Path dir) throws IOException {
        return Files.walk(dir, 3).anyMatch(p -> p.toString().endsWith(".java"));
    }

/**
 * Checks whether a URL points to an archive (zip, tar.gz, tgz, or tar).
 */
    private boolean isArchiveUrl(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz") || lower.endsWith(".tar");
    }

/**
 * Checks whether a path has an archive file extension.
 */
    private boolean isArchiveFile(Path path) {
        String lower = path.toString().toLowerCase();
        return lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz") || lower.endsWith(".tar");
    }

/**
 * Extracts the repository name from a Git URL.
 */
    private String extractRepoName(String url) {
        if (url.endsWith(".git")) url = url.substring(0, url.length() - 4);
        int lastSlash = url.lastIndexOf('/');
        return lastSlash >= 0 ? url.substring(lastSlash + 1) : url;
    }

/**
 * Extracts the file name from a URL.
 */
    private String extractFileName(String url) {
        int lastSlash = url.lastIndexOf('/');
        return lastSlash >= 0 ? url.substring(lastSlash + 1) : url;
    }
}
