package com.jwcode.core.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LspService - LSP 语言服务接口
 * 
 * 功能说明：
 * 定义语言服务器协议（LSP）服务的基本接口。
 * 提供代码智能提示、跳转、重构等功能。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public interface LspService {
    
    /**
     * 连接到语言服务器
     * 
     * @return 连接结果
     */
    CompletableFuture<Void> connect();
    
    /**
     * 断开与语言服务器的连接
     * 
     * @return 断开结果
     */
    CompletableFuture<Void> disconnect();
    
    /**
     * 检查是否已连接
     * 
     * @return true 如果已连接
     */
    boolean isConnected();
    
    /**
     * 获取悬停信息
     * 
     * @param filePath 文件路径
     * @param line 行号（从 0 开始）
     * @param column 列号（从 0 开始）
     * @return 悬停信息
     */
    CompletableFuture<LspHover> hover(String filePath, int line, int column);
    
    /**
     * 获取定义位置
     * 
     * @param filePath 文件路径
     * @param line 行号
     * @param column 列号
     * @return 定义位置列表
     */
    CompletableFuture<List<LspLocation>> definition(String filePath, int line, int column);
    
    /**
     * 获取引用位置
     * 
     * @param filePath 文件路径
     * @param line 行号
     * @param column 列号
     * @return 引用位置列表
     */
    CompletableFuture<List<LspLocation>> references(String filePath, int line, int column);
    
    /**
     * 重命名符号
     * 
     * @param filePath 文件路径
     * @param line 行号
     * @param column 列号
     * @param newName 新名称
     * @return 工作区编辑结果
     */
    CompletableFuture<LspWorkspaceEdit> rename(String filePath, int line, int column, String newName);
    
    /**
     * 格式化文档
     * 
     * @param filePath 文件路径
     * @return 文本编辑列表
     */
    CompletableFuture<List<LspTextEdit>> format(String filePath);
    
    /**
     * 获取代码操作
     * 
     * @param filePath 文件路径
     * @param line 行号
     * @param column 列号
     * @return 代码操作列表
     */
    CompletableFuture<List<LspCodeAction>> codeAction(String filePath, int line, int column);
    
    /**
     * 打开文档
     * 
     * @param filePath 文件路径
     * @param content 文档内容
     * @param languageId 语言 ID
     * @param version 版本号
     */
    void openDocument(String filePath, String content, String languageId, int version);
    
    /**
     * 关闭文档
     * 
     * @param filePath 文件路径
     */
    void closeDocument(String filePath);
    
    /**
     * 更新文档内容
     * 
     * @param filePath 文件路径
     * @param content 新内容
     * @param version 新版本号
     */
    void updateDocument(String filePath, String content, int version);
}