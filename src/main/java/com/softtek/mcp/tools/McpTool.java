package com.softtek.mcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;

public interface McpTool {
    McpServerFeatures.SyncToolSpecification build();
}
