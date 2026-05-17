package com.jwcode.core.mcp.model;

import java.util.List;

/**
 * MCP 工具调用结果模型
 */
public class McpToolResult {
    private List<Object> content;
    private boolean isError;

    public McpToolResult() {}

    public McpToolResult(List<Object> content, boolean isError) {
        this.content = content;
        this.isError = isError;
    }

    public List<Object> getContent() { return content; }
    public void setContent(List<Object> content) { this.content = content; }

    public boolean isError() { return isError; }
    public void setError(boolean error) { isError = error; }
}
