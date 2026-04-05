package com.jwcode.cli;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 命令执行上下文
 * 
 * 提供命令执行时的上下文信息，包括会话、配置、环境等。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandContext {
    
    /**
     * 当前会话
     */
    private Session session;
    
    /**
     * 环境变量
     */
    @Builder.Default
    private Map<String, String> environment = new HashMap<>();
    
    /**
     * 命令参数
     */
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();
    
    /**
     * 工作目录
     */
    private String workingDirectory;
    
    /**
     * 获取会话
     * 
     * @return 当前会话
     */
    public Session getSession() {
        if (session == null) {
            session = new Session();
        }
        return session;
    }
    
    /**
     * 获取环境变量
     * 
     * @param key 变量名
     * @return 变量值
     */
    public String getEnvironment(String key) {
        return environment.get(key);
    }
    
    /**
     * 设置环境变量
     * 
     * @param key 变量名
     * @param value 变量值
     */
    public void setEnvironment(String key, String value) {
        environment.put(key, value);
    }
    
    /**
     * 获取参数
     * 
     * @param key 参数名
     * @return 参数值
     */
    public Object getParameter(String key) {
        return parameters.get(key);
    }
    
    /**
     * 设置参数
     * 
     * @param key 参数名
     * @param value 参数值
     */
    public void setParameter(String key, Object value) {
        parameters.put(key, value);
    }
    
    /**
     * 获取会话历史
     * 
     * @return 会话历史
     */
    public String getSessionHistory() {
        if (session != null) {
            return String.join("\n", session.getMessages());
        }
        return "";
    }
    
    /**
     * 检查是否请求退出
     * 
     * @return 是否请求退出
     */
    public boolean isExitRequested() {
        return session != null && session.isExitRequested();
    }
    
    /**
     * 会话类 - 模拟会话信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Session {
        
        /**
         * 会话ID
         */
        private String sessionId;
        
        /**
         * 消息历史
         */
        @Builder.Default
        private java.util.List<String> messages = new java.util.ArrayList<>();
        
        /**
         * 工具使用历史
         */
        @Builder.Default
        private java.util.List<String> toolHistory = new java.util.ArrayList<>();
        
        /**
         * 会话记忆
         */
        @Builder.Default
        private Map<String, Object> memory = new HashMap<>();
        
        /**
         * 是否请求退出
         */
        private boolean exitRequested;
        
        /**
         * 会话开始时间
         */
        private long startTime = System.currentTimeMillis();
        
        /**
         * 修改的文件列表
         */
        @Builder.Default
        private java.util.List<String> modifiedFiles = new java.util.ArrayList<>();
        
        /**
         * 读取的文件列表
         */
        @Builder.Default
        private java.util.List<String> readFiles = new java.util.ArrayList<>();
        
        /**
         * 任务列表
         */
        @Builder.Default
        private java.util.List<Map<String, Object>> tasks = new java.util.ArrayList<>();
        
        /**
         * 清除消息历史
         */
        public void clearMessages() {
            messages.clear();
        }
        
        /**
         * 清除工具使用历史
         */
        public void clearToolHistory() {
            toolHistory.clear();
        }
        
        /**
         * 清除会话记忆
         */
        public void clearMemory() {
            memory.clear();
        }
        
        /**
         * 添加消息
         * 
         * @param message 消息内容
         */
        public void addMessage(String message) {
            messages.add(message);
        }
        
        /**
         * 添加工具使用记录
         * 
         * @param toolUsage 工具使用记录
         */
        public void addToolUsage(String toolUsage) {
            toolHistory.add(toolUsage);
        }
        
        /**
         * 设置记忆
         * 
         * @param key 键
         * @param value 值
         */
        public void setMemory(String key, Object value) {
            memory.put(key, value);
        }
        
        /**
         * 获取记忆
         * 
         * @param key 键
         * @return 值
         */
        public Object getMemory(String key) {
            return memory.get(key);
        }
        
        /**
         * 获取最后一条消息
         * 
         * @return 最后一条消息
         */
        public String getLastMessage() {
            if (messages.isEmpty()) {
                return null;
            }
            return messages.get(messages.size() - 1);
        }
        
        /**
         * 设置退出请求状态
         * 
         * @param exitRequested 是否请求退出
         */
        public void setExitRequested(boolean exitRequested) {
            this.exitRequested = exitRequested;
        }
        
        /**
         * 检查是否有待处理的任务
         * 
         * @return 是否有待处理的任务
         */
        public boolean hasPendingTasks() {
            return !tasks.isEmpty();
        }
        
        /**
         * 保存会话
         */
        public void save() {
            // 保存会话的实现
        }
        
        /**
         * 关闭 Agents
         */
        public void shutdownAgents() {
            // 关闭 Agents 的实现
        }
        
        /**
         * 关闭 MCP 连接
         */
        public void closeMcpConnections() {
            // 关闭 MCP 连接的实现
        }
        
        /**
         * 释放资源
         */
        public void releaseResources() {
            // 释放资源的实现
        }
        
        /**
         * 获取会话信息
         * 
         * @return 会话信息
         */
        public Map<String, Object> getSessionInfo() {
            Map<String, Object> info = new HashMap<>();
            info.put("id", sessionId);
            info.put("start_time", startTime);
            return info;
        }
        
        /**
         * 获取会话持续时间（分钟）
         * 
         * @return 持续时间（分钟）
         */
        public long getDurationMinutes() {
            return (System.currentTimeMillis() - startTime) / 60000;
        }
        
        /**
         * 获取用户消息数量
         * 
         * @return 用户消息数量
         */
        public int getUserMessageCount() {
            return messages.size() / 2; // 简化计算
        }
        
        /**
         * 获取助手消息数量
         * 
         * @return 助手消息数量
         */
        public int getAssistantMessageCount() {
            return messages.size() / 2; // 简化计算
        }
        
        /**
         * 获取工具使用情况
         * 
         * @return 工具使用统计
         */
        public Map<String, Integer> getToolUsage() {
            Map<String, Integer> usage = new HashMap<>();
            for (String tool : toolHistory) {
                usage.merge(tool, 1, Integer::sum);
            }
            return usage;
        }
        
        /**
         * 获取费用信息
         * 
         * @return 费用信息
         */
        public Map<String, Object> getCostInfo() {
            Map<String, Object> costInfo = new HashMap<>();
            costInfo.put("input_tokens", 10000L);
            costInfo.put("output_tokens", 5000L);
            costInfo.put("input_cost", 0.015);
            costInfo.put("output_cost", 0.03);
            costInfo.put("cached_tokens", 0L);
            costInfo.put("cached_savings", 0.0);
            costInfo.put("days_tracked", 1.0);
            costInfo.put("budget_limit", 0.0);
            return costInfo;
        }
        
        /**
         * 获取修改的文件列表
         * 
         * @return 修改的文件列表
         */
        public java.util.List<String> getModifiedFiles() {
            return modifiedFiles;
        }
        
        /**
         * 获取读取的文件列表
         * 
         * @return 读取的文件列表
         */
        public java.util.List<String> getReadFiles() {
            return readFiles;
        }
        
        /**
         * 获取任务列表
         * 
         * @return 任务列表
         */
        public java.util.List<Map<String, Object>> getTasks() {
            return tasks;
        }
        
        /**
         * 获取会话亮点
         * 
         * @return 会话亮点
         */
        public java.util.List<String> getHighlights() {
            java.util.List<String> highlights = new java.util.ArrayList<>();
            highlights.add("Session highlights");
            return highlights;
        }
    }
}
