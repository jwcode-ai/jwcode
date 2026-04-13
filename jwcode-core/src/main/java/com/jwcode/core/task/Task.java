package com.jwcode.core.task;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 任务实体类
 * 
 * 表示一个可执行的任务，包含任务的所有元数据和状态信息。
 * 支持任务层级结构（通过 parentId 实现子任务）。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Data
public class Task implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 任务唯一标识符（UUID）
     */
    private String id;
    
    /**
     * 任务标题
     */
    private String title;
    
    /**
     * 任务详细描述
     */
    private String description;
    
    /**
     * 任务状态
     */
    private TaskStatus status;
    
    /**
     * 任务优先级（1-10，数值越大优先级越高）
     */
    private int priority;
    
    /**
     * 任务标签列表
     */
    private List<String> tags;
    
    /**
     * 父任务ID（用于支持子任务）
     */
    private String parentId;
    
    /**
     * 任务进度（0-100）
     */
    private int progress;
    
    /**
     * 任务输出内容（使用 StringBuilder 支持高效追加）
     */
    private StringBuilder output;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 开始执行时间
     */
    private LocalDateTime startedAt;
    
    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
    
    /**
     * 默认构造函数
     */
    public Task() {
        this.id = UUID.randomUUID().toString();
        this.status = TaskStatus.PENDING;
        this.priority = 5;
        this.tags = new ArrayList<>();
        this.progress = 0;
        this.output = new StringBuilder();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 创建任务的便捷构造函数
     * 
     * @param title 任务标题
     * @param description 任务描述
     */
    public Task(String title, String description) {
        this();
        this.title = title;
        this.description = description;
    }
    
    /**
     * 创建子任务的构造函数
     * 
     * @param title 任务标题
     * @param description 任务描述
     * @param parentId 父任务ID
     */
    public Task(String title, String description, String parentId) {
        this(title, description);
        this.parentId = parentId;
    }
    
    /**
     * 更新任务状态并记录时间
     * 
     * @param status 新状态
     */
    public void updateStatus(TaskStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
        
        if (status == TaskStatus.RUNNING && this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
        
        if (status.isFinished()) {
            this.completedAt = LocalDateTime.now();
            if (this.progress < 100 && status == TaskStatus.COMPLETED) {
                this.progress = 100;
            }
        }
    }
    
    /**
     * 更新任务进度
     * 
     * @param progress 进度值（0-100）
     */
    public void updateProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 追加输出内容
     * 
     * @param content 要追加的内容
     */
    public void appendOutput(String content) {
        if (content != null && !content.isEmpty()) {
            this.output.append(content);
            this.updatedAt = LocalDateTime.now();
        }
    }
    
    /**
     * 追加输出内容并换行
     * 
     * @param content 要追加的内容
     */
    public void appendOutputLine(String content) {
        if (content != null && !content.isEmpty()) {
            this.output.append(content).append("\n");
            this.updatedAt = LocalDateTime.now();
        }
    }
    
    /**
     * 获取输出内容字符串
     * 
     * @return 输出内容
     */
    public String getOutputString() {
        return this.output.toString();
    }
    
    /**
     * 清空输出内容
     */
    public void clearOutput() {
        this.output.setLength(0);
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 添加标签
     * 
     * @param tag 标签
     */
    public void addTag(String tag) {
        if (tag != null && !tag.trim().isEmpty() && !this.tags.contains(tag)) {
            this.tags.add(tag);
            this.updatedAt = LocalDateTime.now();
        }
    }
    
    /**
     * 移除标签
     * 
     * @param tag 标签
     */
    public void removeTag(String tag) {
        if (this.tags.remove(tag)) {
            this.updatedAt = LocalDateTime.now();
        }
    }
    
    /**
     * 检查是否为子任务
     * 
     * @return true 如果有父任务ID
     */
    public boolean isSubTask() {
        return this.parentId != null && !this.parentId.isEmpty();
    }
    
    /**
     * 获取任务持续时间（毫秒）
     * 
     * @return 持续时间，如果任务未开始或未结束返回 -1
     */
    public long getDurationMillis() {
        if (this.startedAt == null) {
            return -1;
        }
        LocalDateTime endTime = this.completedAt != null ? this.completedAt : LocalDateTime.now();
        return java.time.Duration.between(this.startedAt, endTime).toMillis();
    }
    
    /**
     * 标记任务为已完成
     */
    public void markCompleted() {
        updateStatus(TaskStatus.COMPLETED);
        this.progress = 100;
    }
    
    /**
     * 标记任务为失败
     * 
     * @param errorMessage 错误信息
     */
    public void markFailed(String errorMessage) {
        updateStatus(TaskStatus.FAILED);
        if (errorMessage != null) {
            appendOutputLine("[ERROR] " + errorMessage);
        }
    }
    
    /**
     * 标记任务为已停止
     */
    public void markStopped() {
        updateStatus(TaskStatus.STOPPED);
    }
    
    /**
     * 标记任务为运行中
     */
    public void markRunning() {
        updateStatus(TaskStatus.RUNNING);
    }
    
    @Override
    public String toString() {
        return String.format("Task[id=%s, title=%s, status=%s, progress=%d%%]", 
            id, title, status, progress);
    }
}
