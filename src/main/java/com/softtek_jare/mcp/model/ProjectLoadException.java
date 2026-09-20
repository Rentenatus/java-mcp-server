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

package com.softtek_jare.mcp.model;

/**
 * The {@code ProjectLoadException} class.
 *
 * @author Alejandro Ferreira
 */
public class ProjectLoadException extends Exception {
    private final String type;
    private final String detail;

/**
 * Constructs a new exception with a type code and message.
 */
    public ProjectLoadException(String type, String message) {
        this(type, message, null);
    }

/**
 * Constructs a new exception with a type code, message, and detail.
 */
    public ProjectLoadException(String type, String message, String detail) {
        super(message);
        this.type = type;
        this.detail = detail;
    }

/**
 * Returns the error type code.
 */
    public String getType() { return type; }
/**
 * Returns the error detail.
 */
    public String getDetail() { return detail; }
}
