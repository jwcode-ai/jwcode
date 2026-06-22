package com.jwcode.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PlanTask — 计划任务模型（支持树形层级结构）
 *
 * <p>对应前端 PlanTask 类型，用于 WebSocket plan_tasks 消息的序列化。
 * 支持 children 字段实现树形展示。</p>
 */
public class PlanTask {

    private String id;
    private String title;
    private String description;
    private String status; // pending / running / completed / failed / skipped
    private String agentType;
    private List<String> dependencies;
    /** 子任务列表（树形结构支持） */
    private List<PlanTask> children;
    /** 任务上下文（文件路径、依赖模块、约束条件等） */
    private Map<String, String> context;
    private String result;
    private String error;
    private Long startedAt;
    private Long completedAt;
    private Integer progress;
    private List<String> logs;

    public PlanTask() {
        this.dependencies = new ArrayList<>();
        this.children = new ArrayList<>();
        this.context = new HashMap<>();
    }

    public PlanTask(String id, String title, String description, String status, String agentType) {
        this();
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.agentType = agentType;
    }

    // ==================== Builder ====================

    public static PlanTaskBuilder builder() {
        return new PlanTaskBuilder();
    }

    public static class PlanTaskBuilder {
        private String id;
        private String title;
        private String description;
        private String status = "pending";
        private String agentType = "orchestrator";
        private List<String> dependencies = new ArrayList<>();
        private List<PlanTask> children = new ArrayList<>();
        private String result;
        private String error;
        private Long startedAt;
        private Long completedAt;
        private Integer progress;
        private List<String> logs;
        private Map<String, String> context;

        PlanTaskBuilder() {}

        public PlanTaskBuilder id(String id) { this.id = id; return this; }
        public PlanTaskBuilder title(String title) { this.title = title; return this; }
        public PlanTaskBuilder description(String description) { this.description = description; return this; }
        public PlanTaskBuilder status(String status) { this.status = status; return this; }
        public PlanTaskBuilder agentType(String agentType) { this.agentType = agentType; return this; }
        public PlanTaskBuilder dependencies(List<String> dependencies) { this.dependencies = dependencies; return this; }
        public PlanTaskBuilder children(List<PlanTask> children) { this.children = children; return this; }
        public PlanTaskBuilder addChild(PlanTask child) { this.children.add(child); return this; }
        public PlanTaskBuilder result(String result) { this.result = result; return this; }
        public PlanTaskBuilder error(String error) { this.error = error; return this; }
        public PlanTaskBuilder startedAt(Long startedAt) { this.startedAt = startedAt; return this; }
        public PlanTaskBuilder completedAt(Long completedAt) { this.completedAt = completedAt; return this; }
        public PlanTaskBuilder progress(Integer progress) { this.progress = progress; return this; }
        public PlanTaskBuilder logs(List<String> logs) { this.logs = logs; return this; }
        public PlanTaskBuilder context(Map<String, String> context) { this.context = context; return this; }

        public PlanTask build() {
            PlanTask task = new PlanTask();
            task.id = this.id;
            task.title = this.title;
            task.description = this.description;
            task.status = this.status;
            task.agentType = this.agentType;
            task.dependencies = this.dependencies;
            task.children = this.children;
            task.result = this.result;
            task.error = this.error;
            task.startedAt = this.startedAt;
            task.completedAt = this.completedAt;
            task.progress = this.progress;
            task.logs = this.logs;
            task.context = this.context != null ? new HashMap<>(this.context) : new HashMap<>();
            return task;
        }
    }

    // ==================== Getters & Setters ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }

    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }

    public List<PlanTask> getChildren() { return children; }
    public void setChildren(List<PlanTask> children) { this.children = children; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public Long getStartedAt() { return startedAt; }
    public void setStartedAt(Long startedAt) { this.startedAt = startedAt; }

    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public List<String> getLogs() { return logs; }
    public void setLogs(List<String> logs) { this.logs = logs; }

    public Map<String, String> getContext() { return context; }
    public void setContext(Map<String, String> context) { this.context = context; }
}
