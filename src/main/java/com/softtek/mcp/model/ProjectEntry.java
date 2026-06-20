package com.softtek.mcp.model;

public record ProjectEntry(
        String name,
        String alias,
        java.time.Instant expiryDate,
        java.nio.file.Path projectDir,
        spoon.Launcher launcher,
        spoon.reflect.CtModel model,
        String buildType
) {}
