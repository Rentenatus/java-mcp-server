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

package com.softtek.mcp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link Fingerprint} record.
 *
 * @author Janusch Rentenatus
 */
class FingerprintTest {

    @Test
    void equalsAndHashCodeWork() {
        Fingerprint a = new Fingerprint(1000L, 200L);
        Fingerprint b = new Fingerprint(1000L, 200L);
        Fingerprint c = new Fingerprint(1001L, 200L);
        Fingerprint d = new Fingerprint(1000L, 201L);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, d);
    }

    @Test
    void accessorsReturnValues() {
        Fingerprint fp = new Fingerprint(42L, 99L);
        assertEquals(42L, fp.lastModified());
        assertEquals(99L, fp.fileSize());
    }

    @Test
    void toStringContainsValues() {
        Fingerprint fp = new Fingerprint(100L, 200L);
        String s = fp.toString();
        assertTrue(s.contains("100"));
        assertTrue(s.contains("200"));
    }
}
