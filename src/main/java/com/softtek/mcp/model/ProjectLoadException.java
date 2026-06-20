package com.softtek.mcp.model;

public class ProjectLoadException extends Exception {
    private final String type;
    private final String detail;

    public ProjectLoadException(String type, String message) {
        this(type, message, null);
    }

    public ProjectLoadException(String type, String message, String detail) {
        super(message);
        this.type = type;
        this.detail = detail;
    }

    public String getType() { return type; }
    public String getDetail() { return detail; }
}
