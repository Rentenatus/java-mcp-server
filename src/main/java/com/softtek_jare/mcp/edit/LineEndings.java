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

/**
 * Utility for line-ending normalization in edit operations.
 *
 * <p>The server is designed for many projects with varying line-ending
 * conventions (LF on Linux/macOS, CRLF on Windows, occasionally mixed).
 * All text-matching operations strip CR from both source and target
 * before comparison so that LF is always recognized as the line separator.
 * When writing to disk, the file's existing line-ending convention is preserved.
 *
 * @author Janusch Rentenatus
 */
public final class LineEndings {

    private LineEndings() {}

    /**
     * Removes all carriage-return characters from the input string.
     * Used for normalizing both file content and search/replace strings
     * before any string comparison.
     *
     * @param text the input text, possibly containing CR or CRLF
     * @return the text with all CR characters removed (LF-only)
     */
    public static String normalizeForMatch(String text) {
        if (text == null) return null;
        return text.replace("\r", "");
    }

    /**
     * Detects the dominant line-ending convention in the given text.
     *
     * @param text the text to analyze
     * @return {@code "\r\n"} if CRLF is present, {@code "\n"} otherwise
     */
    public static String detectLineEnding(String text) {
        if (text == null || text.isEmpty()) return "\n";
        return text.contains("\r\n") ? "\r\n" : "\n";
    }

    /**
     * Adapts the given content to use the specified line ending.
     * If the content already uses the target line ending, it is returned unchanged.
     * Otherwise, all CRLF and lone CR are normalized to the target ending.
     *
     * @param content       the content to adapt
     * @param targetEnding  the desired line ending ({@code "\r\n"} or {@code "\n"})
     * @return the content with the target line ending
     */
    public static String preserveOnWrite(String content, String targetEnding) {
        if (content == null) return null;
        if (targetEnding == null || targetEnding.equals("\n")) {
            return content.replace("\r\n", "\n").replace("\r", "\n");
        }
        // target is CRLF: first normalize to LF, then convert to CRLF
        String lfOnly = content.replace("\r\n", "\n").replace("\r", "\n");
        return lfOnly.replace("\n", "\r\n");
    }
}
