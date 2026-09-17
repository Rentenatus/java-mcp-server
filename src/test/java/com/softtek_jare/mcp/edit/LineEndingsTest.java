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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LineEndings}.
 *
 * @author Janusch Rentenatus
 */
class LineEndingsTest {

    // --- normalizeForMatch ---

    @Test
    void normalizeForMatchRemovesCRLF() {
        String input = "line1\r\nline2\r\nline3";
        assertEquals("line1\nline2\nline3", LineEndings.normalizeForMatch(input));
    }

    @Test
    void normalizeForMatchRemovesLoneCR() {
        // Lone CR (old Mac style) is stripped, not converted to LF
        String input = "line1\rline2\rline3";
        assertEquals("line1line2line3", LineEndings.normalizeForMatch(input));
    }

    @Test
    void normalizeForMatchLeavesLFOnly() {
        String input = "line1\nline2\nline3";
        assertEquals("line1\nline2\nline3", LineEndings.normalizeForMatch(input));
    }

    @Test
    void normalizeForMatchHandlesNull() {
        assertNull(LineEndings.normalizeForMatch(null));
    }

    @Test
    void normalizeForMatchHandlesEmpty() {
        assertEquals("", LineEndings.normalizeForMatch(""));
    }

    @Test
    void normalizeForMatchHandlesMixedEndings() {
        // CRLF -> LF, lone CR stripped (no replacement)
        String input = "line1\r\nline2\nline3\rline4";
        assertEquals("line1\nline2\nline3line4", LineEndings.normalizeForMatch(input));
    }

    @Test
    void normalizeForMatchNoTrailingNewline() {
        String input = "single line";
        assertEquals("single line", LineEndings.normalizeForMatch(input));
    }

    // --- detectLineEnding ---

    @Test
    void detectLineEndingReturnsCRLF() {
        assertEquals("\r\n", LineEndings.detectLineEnding("line1\r\nline2\r\n"));
    }

    @Test
    void detectLineEndingReturnsLF() {
        assertEquals("\n", LineEndings.detectLineEnding("line1\nline2\n"));
    }

    @Test
    void detectLineEndingDefaultsToLFForNoNewlines() {
        assertEquals("\n", LineEndings.detectLineEnding("no newlines here"));
    }

    @Test
    void detectLineEndingDefaultsToLFForNull() {
        assertEquals("\n", LineEndings.detectLineEnding(null));
    }

    @Test
    void detectLineEndingDefaultsToLFForEmpty() {
        assertEquals("\n", LineEndings.detectLineEnding(""));
    }

    @Test
    void detectLineEndingReturnsCRLFForMixed() {
        assertEquals("\r\n", LineEndings.detectLineEnding("line1\r\nline2\n"));
    }

    // --- preserveOnWrite ---

    @Test
    void preserveOnWriteConvertsCRLFToLF() {
        String content = "line1\r\nline2\r\nline3";
        assertEquals("line1\nline2\nline3", LineEndings.preserveOnWrite(content, "\n"));
    }

    @Test
    void preserveOnWriteConvertsLFToCRLF() {
        String content = "line1\nline2\nline3";
        assertEquals("line1\r\nline2\r\nline3", LineEndings.preserveOnWrite(content, "\r\n"));
    }

    @Test
    void preserveOnWriteKeepsCRLFWhenTargetIsCRLF() {
        String content = "line1\r\nline2\r\nline3";
        assertEquals("line1\r\nline2\r\nline3", LineEndings.preserveOnWrite(content, "\r\n"));
    }

    @Test
    void preserveOnWriteKeepsLFWhenTargetIsLF() {
        String content = "line1\nline2\nline3";
        assertEquals("line1\nline2\nline3", LineEndings.preserveOnWrite(content, "\n"));
    }

    @Test
    void preserveOnWriteNormalizesMixedToCRLF() {
        // CRLF, LF, and lone CR all normalized to CRLF
        String content = "line1\r\nline2\nline3\rline4";
        assertEquals("line1\r\nline2\r\nline3\r\nline4", LineEndings.preserveOnWrite(content, "\r\n"));
    }

    @Test
    void preserveOnWriteNormalizesMixedToLF() {
        String content = "line1\r\nline2\nline3\rline4";
        assertEquals("line1\nline2\nline3\nline4", LineEndings.preserveOnWrite(content, "\n"));
    }

    @Test
    void preserveOnWriteHandlesNull() {
        assertNull(LineEndings.preserveOnWrite(null, "\n"));
    }

    @Test
    void preserveOnWriteHandlesNullEnding() {
        String content = "line1\nline2";
        assertEquals("line1\nline2", LineEndings.preserveOnWrite(content, null));
    }
}
