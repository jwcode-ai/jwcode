package com.jwcode.core.mcp.model;

/**
 * MCP 资源内容模型
 */
public class McpResourceContent {
    private String uri;
    private String mimeType;
    private String text;
    private byte[] blob;

    public McpResourceContent() {}

    public McpResourceContent(String uri, String mimeType, String text) {
        this.uri = uri;
        this.mimeType = mimeType;
        this.text = text;
    }

    public McpResourceContent(String uri, String mimeType, byte[] blob) {
        this.uri = uri;
        this.mimeType = mimeType;
        this.blob = blob;
    }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public byte[] getBlob() { return blob; }
    public void setBlob(byte[] blob) { this.blob = blob; }
}
