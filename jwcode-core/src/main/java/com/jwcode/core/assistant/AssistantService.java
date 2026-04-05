package com.jwcode.core.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AssistantService - Assistant 模式服务
 * 
 * 功能说明：
 * AI 助手协作模式，支持多助手协同完成任务，每个助手专注于特定领域。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class AssistantService {
    
    private final ExecutorService executor;
    private final Map<String, Assistant> assistants;
    private final Map<String, AssistantTask> activeTasks;
    private final AtomicBoolean running;
    private AssistantMode currentMode;
    
    public AssistantService() {
        this.executor = Executors.newFixedThreadPool(8);
        this.assistants = new ConcurrentHashMap<>();
        this.activeTasks = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
        this.currentMode = AssistantMode.COLLABORATIVE;
        registerDefaultAssistants();
    }
    
    /**
     * 注册默认助手
     */
    private void registerDefaultAssistants() {
        // 代码助手
        assistants.put("coder", new Assistant(
                "coder",
                "代码助手",
                "专注于代码编写、重构和最佳实践",
                AssistantSpecialty.CODING
        ));
        
        // 测试助手
        assistants.put("tester", new Assistant(
                "tester",
                "测试助手",
                "专注于单元测试、集成测试和测试覆盖率",
                AssistantSpecialty.TESTING
        ));
        
        // 文档助手
        assistants.put("writer", new Assistant(
                "writer",
                "文档助手",
                "专注于技术文档、注释和 API 文档编写",
                AssistantSpecialty.DOCUMENTATION
        ));
        
        // 安全助手
        assistants.put("security", new Assistant(
                "security",
                "安全助手",
                "专注于安全审计、漏洞检测和安全最佳实践",
                AssistantSpecialty.SECURITY
        ));
        
        // 性能助手
        assistants.put("performance", new Assistant(
                "performance",
                "性能助手",
                "专注于性能分析、优化建议和瓶颈检测",
                AssistantSpecialty.PERFORMANCE
        ));
        
        // 架构助手
        assistants.put("architect", new Assistant(
                "architect",
                "架构助手",
                "专注于系统设计、架构模式和最佳实践",
                AssistantSpecialty.ARCHITECTURE
        ));
    }
    
    /**
     * 启动助手服务
     */
    public void start() {
        running.set(true);
    }
    
    /**
     * 停止助手服务
     */
    public void stop() {
        running.set(false);
        for (AssistantTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
    }
    
    /**
     * 设置助手模式
     */
    public void setMode(AssistantMode mode) {
        this.currentMode = mode;
    }
    
    /**
     * 获取当前模式
     */
    public AssistantMode getCurrentMode() {
        return currentMode;
    }
    
    /**
     * 创建任务
     */
    public CompletableFuture<AssistantTask> createTask(String description, List<String> assistantIds) {
        return CompletableFuture.supplyAsync(() -> {
            String taskId = "task_" + System.currentTimeMillis();
            
            List<Assistant> taskAssistants = new ArrayList<>();
            for (String id : assistantIds) {
                Assistant assistant = assistants.get(id);
                if (assistant != null) {
                    taskAssistants.add(assistant);
                }
            }
            
            AssistantTask task = new AssistantTask(taskId, description, taskAssistants);
            activeTasks.put(taskId, task);
            
            return task;
        }, executor);
    }
    
    /**
     * 执行任务
     */
    public CompletableFuture<AssistantTaskResult> executeTask(String taskId) {
        return CompletableFuture.supplyAsync(() -> {
            AssistantTask task = activeTasks.get(taskId);
            if (task == null) {
                return new AssistantTaskResult(false, "任务不存在：" + taskId);
            }
            
            task.setStatus(TaskStatus.RUNNING);
            List<String> results = new ArrayList<>();
            
            // 根据模式执行任务
            switch (currentMode) {
                case COLLABORATIVE:
                    // 所有助手协同工作
                    for (Assistant assistant : task.assistants) {
                        String result = assistant.process(task.description);
                        results.add(result);
                        task.addResult(assistant.id, result);
                    }
                    break;
                    
                case SEQUENTIAL:
                    // 顺序执行
                    for (Assistant assistant : task.assistants) {
                        String result = assistant.process(task.description);
                        results.add(result);
                        task.addResult(assistant.id, result);
                    }
                    break;
                    
                case PARALLEL:
                    // 并行执行
                    List<CompletableFuture<String>> futures = new ArrayList<>();
                    for (Assistant assistant : task.assistants) {
                        futures.add(CompletableFuture.supplyAsync(
                                () -> assistant.process(task.description), executor));
                    }
                    
                    for (int i = 0; i < task.assistants.size(); i++) {
                        String result = futures.get(i).join();
                        results.add(result);
                        task.addResult(task.assistants.get(i).id, result);
                    }
                    break;
            }
            
            task.setStatus(TaskStatus.COMPLETED);
            return new AssistantTaskResult(true, results);
        }, executor);
    }
    
    /**
     * 获取助手列表
     */
    public List<Assistant> getAssistants() {
        return new ArrayList<>(assistants.values());
    }
    
    /**
     * 获取活动任务列表
     */
    public List<AssistantTask> getActiveTasks() {
        return new ArrayList<>(activeTasks.values());
    }
    
    /**
     * 获取任务状态
     */
    public AssistantTask getTaskStatus(String taskId) {
        return activeTasks.get(taskId);
    }
    
    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId) {
        AssistantTask task = activeTasks.remove(taskId);
        if (task != null) {
            task.cancel();
            return true;
        }
        return false;
    }
    
    /**
     * 关闭服务
     */
    public void shutdown() {
        stop();
        executor.shutdown();
    }
    
    /**
     * 助手模式枚举
     */
    public enum AssistantMode {
        COLLABORATIVE,  // 协同模式
        SEQUENTIAL,     // 顺序模式
        PARALLEL        // 并行模式
    }
    
    /**
     * 助手专业领域枚举
     */
    public enum AssistantSpecialty {
        CODING,
        TESTING,
        DOCUMENTATION,
        SECURITY,
        PERFORMANCE,
        ARCHITECTURE,
        DEVOPS,
        DATABASE
    }
    
    /**
     * 助手类
     */
    public static class Assistant {
        public final String id;
        public final String name;
        public final String description;
        public final AssistantSpecialty specialty;
        
        public Assistant(String id, String name, String description, AssistantSpecialty specialty) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.specialty = specialty;
        }
        
        public String process(String task) {
            // 模拟助手处理任务
            return "[" + name + "] 处理任务：" + task;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("name", name);
            map.put("description", description);
            map.put("specialty", specialty.toString());
            return map;
        }
    }
    
    /**
     * 助手任务类
     */
    public static class AssistantTask {
        public final String id;
        public final String description;
        public final List<Assistant> assistants;
        public final Map<String, String> results;
        public TaskStatus status;
        public long createdAt;
        public long completedAt;
        
        public AssistantTask(String id, String description, List<Assistant> assistants) {
            this.id = id;
            this.description = description;
            this.assistants = assistants;
            this.results = new ConcurrentHashMap<>();
            this.status = TaskStatus.PENDING;
            this.createdAt = System.currentTimeMillis();
        }
        
        public void addResult(String assistantId, String result) {
            results.put(assistantId, result);
        }
        
        public void setStatus(TaskStatus status) {
            this.status = status;
            if (status == TaskStatus.COMPLETED) {
                this.completedAt = System.currentTimeMillis();
            }
        }
        
        public void cancel() {
            this.status = TaskStatus.CANCELLED;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("description", description);
            map.put("status", status.toString());
            map.put("assistantCount", assistants.size());
            map.put("results", results);
            return map;
        }
    }
    
    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    /**
     * 任务结果类
     */
    public static class AssistantTaskResult {
        public final boolean success;
        public final Object data;
        
        public AssistantTaskResult(boolean success, Object data) {
            this.success = success;
            this.data = data;
        }
    }
}