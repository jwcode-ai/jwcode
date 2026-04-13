package com.jwcode.core.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 任务存储类
 * 
 * 负责任务的内存存储和持久化管理。
 * 任务数据会保存到 .jwcode/tasks.json 文件中。
 * 
 * 线程安全：使用 ConcurrentHashMap 确保多线程安全
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class TaskStore {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskStore.class);
    private static final String DEFAULT_TASKS_FILE = ".jwcode/tasks.json";
    
    private final Map<String, Task> tasks;
    private final Path storagePath;
    private final ObjectMapper objectMapper;
    private volatile boolean autoSaveEnabled;
    
    /**
     * 单例实例
     */
    private static volatile TaskStore instance;
    private static final Object INSTANCE_LOCK = new Object();
    
    /**
     * 获取单例实例
     * 
     * @return TaskStore 实例
     */
    public static TaskStore getInstance() {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new TaskStore();
                }
            }
        }
        return instance;
    }
    
    /**
     * 获取指定工作目录的 TaskStore 实例
     * 
     * @param workingDirectory 工作目录
     * @return TaskStore 实例
     */
    public static TaskStore getInstance(Path workingDirectory) {
        return new TaskStore(workingDirectory.resolve(DEFAULT_TASKS_FILE));
    }
    
    /**
     * 默认构造函数（使用当前工作目录）
     */
    public TaskStore() {
        this(Paths.get(System.getProperty("user.dir"), DEFAULT_TASKS_FILE));
    }
    
    /**
     * 指定存储路径的构造函数
     * 
     * @param storagePath 存储文件路径
     */
    public TaskStore(Path storagePath) {
        this.tasks = new ConcurrentHashMap<>();
        this.storagePath = storagePath;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);
        this.autoSaveEnabled = true;
        
        // 启动时加载已有任务
        load();
    }
    
    /**
     * 创建新任务
     * 
     * @param task 任务对象
     * @return 创建的任务
     */
    public Task create(Task task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        
        if (task.getId() == null || task.getId().isEmpty()) {
            throw new IllegalArgumentException("Task ID cannot be null or empty");
        }
        
        tasks.put(task.getId(), task);
        logger.debug("Task created: {}", task.getId());
        
        if (autoSaveEnabled) {
            save();
        }
        
        return task;
    }
    
    /**
     * 更新任务
     * 
     * @param task 任务对象
     * @return 更新后的任务，如果任务不存在返回 null
     */
    public Task update(Task task) {
        if (task == null || task.getId() == null) {
            return null;
        }
        
        if (!tasks.containsKey(task.getId())) {
            logger.warn("Task not found for update: {}", task.getId());
            return null;
        }
        
        task.setUpdatedAt(java.time.LocalDateTime.now());
        tasks.put(task.getId(), task);
        logger.debug("Task updated: {}", task.getId());
        
        if (autoSaveEnabled) {
            save();
        }
        
        return task;
    }
    
    /**
     * 删除任务
     * 
     * @param taskId 任务ID
     * @return true 如果删除成功
     */
    public boolean delete(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return false;
        }
        
        Task removed = tasks.remove(taskId);
        if (removed != null) {
            logger.debug("Task deleted: {}", taskId);
            if (autoSaveEnabled) {
                save();
            }
            return true;
        }
        
        return false;
    }
    
    /**
     * 获取任务
     * 
     * @param taskId 任务ID
     * @return 任务对象，如果不存在返回 null
     */
    public Task get(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return null;
        }
        return tasks.get(taskId);
    }
    
    /**
     * 列出所有任务
     * 
     * @return 任务列表
     */
    public List<Task> list() {
        return new ArrayList<>(tasks.values());
    }
    
    /**
     * 根据状态列出任务
     * 
     * @param status 任务状态
     * @return 符合条件的任务列表
     */
    public List<Task> listByStatus(TaskStatus status) {
        return tasks.values().stream()
            .filter(task -> task.getStatus() == status)
            .collect(Collectors.toList());
    }
    
    /**
     * 列出活跃任务（PENDING 或 RUNNING）
     * 
     * @return 活跃任务列表
     */
    public List<Task> listActive() {
        return tasks.values().stream()
            .filter(task -> task.getStatus().isActive())
            .collect(Collectors.toList());
    }
    
    /**
     * 列出子任务
     * 
     * @param parentId 父任务ID
     * @return 子任务列表
     */
    public List<Task> listSubTasks(String parentId) {
        if (parentId == null || parentId.isEmpty()) {
            return new ArrayList<>();
        }
        return tasks.values().stream()
            .filter(task -> parentId.equals(task.getParentId()))
            .collect(Collectors.toList());
    }
    
    /**
     * 根据标签列出任务
     * 
     * @param tag 标签
     * @return 符合条件的任务列表
     */
    public List<Task> listByTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return new ArrayList<>();
        }
        return tasks.values().stream()
            .filter(task -> task.getTags() != null && task.getTags().contains(tag))
            .collect(Collectors.toList());
    }
    
    /**
     * 检查任务是否存在
     * 
     * @param taskId 任务ID
     * @return true 如果存在
     */
    public boolean exists(String taskId) {
        return taskId != null && tasks.containsKey(taskId);
    }
    
    /**
     * 获取任务总数
     * 
     * @return 任务数量
     */
    public int size() {
        return tasks.size();
    }
    
    /**
     * 清空所有任务
     */
    public void clear() {
        tasks.clear();
        logger.info("All tasks cleared");
        if (autoSaveEnabled) {
            save();
        }
    }
    
    /**
     * 保存任务到文件
     */
    public synchronized void save() {
        try {
            // 确保目录存在
            Path parentDir = storagePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            // 转换为可序列化的列表
            List<TaskData> taskDataList = tasks.values().stream()
                .map(TaskData::fromTask)
                .collect(Collectors.toList());
            
            objectMapper.writeValue(storagePath.toFile(), taskDataList);
            logger.debug("Tasks saved to: {}", storagePath);
            
        } catch (IOException e) {
            logger.error("Failed to save tasks: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 从文件加载任务
     */
    public synchronized void load() {
        if (!Files.exists(storagePath)) {
            logger.debug("No existing tasks file found at: {}", storagePath);
            return;
        }
        
        try {
            List<TaskData> taskDataList = objectMapper.readValue(
                storagePath.toFile(),
                new TypeReference<List<TaskData>>() {}
            );
            
            tasks.clear();
            for (TaskData data : taskDataList) {
                Task task = data.toTask();
                if (task != null && task.getId() != null) {
                    tasks.put(task.getId(), task);
                }
            }
            
            logger.info("Loaded {} tasks from: {}", tasks.size(), storagePath);
            
        } catch (IOException e) {
            logger.error("Failed to load tasks: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 设置是否自动保存
     * 
     * @param enabled true 启用自动保存
     */
    public void setAutoSaveEnabled(boolean enabled) {
        this.autoSaveEnabled = enabled;
    }
    
    /**
     * 获取存储路径
     * 
     * @return 存储文件路径
     */
    public Path getStoragePath() {
        return storagePath;
    }
    
    /**
     * 用于序列化的任务数据类
     */
    private static class TaskData {
        public String id;
        public String title;
        public String description;
        public String status;
        public int priority;
        public List<String> tags;
        public String parentId;
        public int progress;
        public String output;
        public java.time.LocalDateTime createdAt;
        public java.time.LocalDateTime updatedAt;
        public java.time.LocalDateTime startedAt;
        public java.time.LocalDateTime completedAt;
        
        public static TaskData fromTask(Task task) {
            TaskData data = new TaskData();
            data.id = task.getId();
            data.title = task.getTitle();
            data.description = task.getDescription();
            data.status = task.getStatus() != null ? task.getStatus().name() : TaskStatus.PENDING.name();
            data.priority = task.getPriority();
            data.tags = task.getTags();
            data.parentId = task.getParentId();
            data.progress = task.getProgress();
            data.output = task.getOutputString();
            data.createdAt = task.getCreatedAt();
            data.updatedAt = task.getUpdatedAt();
            data.startedAt = task.getStartedAt();
            data.completedAt = task.getCompletedAt();
            return data;
        }
        
        public Task toTask() {
            Task task = new Task();
            task.setId(id);
            task.setTitle(title);
            task.setDescription(description);
            task.setStatus(status != null ? TaskStatus.valueOf(status) : TaskStatus.PENDING);
            task.setPriority(priority);
            task.setTags(tags != null ? new ArrayList<>(tags) : new ArrayList<>());
            task.setParentId(parentId);
            task.setProgress(progress);
            task.appendOutput(output != null ? output : "");
            task.setCreatedAt(createdAt);
            task.setUpdatedAt(updatedAt);
            task.setStartedAt(startedAt);
            task.setCompletedAt(completedAt);
            return task;
        }
    }
}
