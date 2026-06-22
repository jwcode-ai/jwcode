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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
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
    
    /** 任务变更监听器列表 */
    private final List<Consumer<TaskEvent>> taskListeners = new CopyOnWriteArrayList<>();
    
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
    
    // ==================== 黑板事件系统 (Blackboard Pattern) ====================

    /**
     * 任务事件类型枚举 — 替代旧的 String action。
     */
    public enum EventType {
        CREATED,      // 任务创建
        UPDATED,      // 任务更新（含状态变更）
        STATUS_CHANGED, // 仅状态变化（UPDATED 的子集）
        DELETED,      // 任务删除
        COMPLETED     // 任务完成（状态变为 COMPLETED 时触发 UPDATED + COMPLETED）
    }

    /**
     * 任务变更事件 — 当任务被创建、更新或删除时触发。
     * <p>扩展为包含事件类型枚举、旧状态/新状态、sessionId 等上下文。</p>
     */
    public static class TaskEvent {
        private final EventType eventType;
        private final String action;   // 兼容旧版: "created" | "updated" | "deleted"
        private final Task task;
        private final TaskStatus oldStatus;
        private final TaskStatus newStatus;
        private final String sessionId;

        public TaskEvent(EventType eventType, Task task) {
            this(eventType, task, null, null, null);
        }

        public TaskEvent(EventType eventType, Task task, TaskStatus oldStatus, TaskStatus newStatus, String sessionId) {
            this.eventType = eventType;
            this.action = eventType.name().toLowerCase();
            this.task = task;
            this.oldStatus = oldStatus;
            this.newStatus = newStatus;
            this.sessionId = sessionId;
        }

        public EventType getEventType() { return eventType; }
        public String getAction() { return action; }
        public Task getTask() { return task; }
        public String getTaskId() { return task != null ? task.getId() : null; }
        public TaskStatus getOldStatus() { return oldStatus; }
        public TaskStatus getNewStatus() { return newStatus; }
        public String getSessionId() { return sessionId; }

        /** 是否为状态变更事件（如 RUNNING → COMPLETED） */
        public boolean isStatusTransition() {
            return oldStatus != null && newStatus != null && oldStatus != newStatus;
        }
    }

    /** 订阅句柄，用于取消订阅 */
    public static class Subscription {
        private final String id;
        private final Consumer<TaskEvent> listener;
        private final java.util.function.Predicate<TaskEvent> filter;

        Subscription(Consumer<TaskEvent> listener, java.util.function.Predicate<TaskEvent> filter) {
            this.id = UUID.randomUUID().toString().substring(0, 8);
            this.listener = listener;
            this.filter = filter;
        }

        public String getId() { return id; }
        boolean matches(TaskEvent event) { return filter == null || filter.test(event); }
        void dispatch(TaskEvent event) { listener.accept(event); }
    }

    /** 所有订阅 */
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    /**
     * 订阅任务变更事件。
     *
     * @param listener 事件处理回调
     * @return 订阅句柄（可调用 unsubscribe 取消）
     */
    public Subscription subscribe(Consumer<TaskEvent> listener) {
        return subscribe(listener, null);
    }

    /**
     * 订阅满足过滤条件的任务变更事件。
     *
     * @param listener 事件处理回调
     * @param filter   事件过滤器（返回 true 才通知）
     * @return 订阅句柄
     */
    public Subscription subscribe(Consumer<TaskEvent> listener, java.util.function.Predicate<TaskEvent> filter) {
        Subscription sub = new Subscription(listener, filter);
        subscriptions.add(sub);
        return sub;
    }

    /**
     * 按 sessionId 订阅任务事件（黑板投影的便捷方法）。
     */
    public Subscription subscribeBySession(String sessionId, Consumer<TaskEvent> listener) {
        return subscribe(listener, event -> sessionId.equals(event.getSessionId()));
    }

    /**
     * 添加任务变更监听器（向后兼容的旧版 API）。
     * <p>内部创建 Subscription 并自动管理生命周期。</p>
     *
     * @param listener 监听器，接收 TaskEvent
     */
    public void addTaskListener(Consumer<TaskEvent> listener) {
        if (listener != null) {
            taskListeners.add(listener);
        }
    }

    /**
     * 移除任务变更监听器（向后兼容的旧版 API）。
     *
     * @param listener 之前注册的监听器
     */
    public void removeTaskListener(Consumer<TaskEvent> listener) {
        taskListeners.remove(listener);
    }

    /**
     * 取消订阅。
     */
    public void unsubscribe(Subscription subscription) {
        if (subscription != null) {
            subscriptions.remove(subscription);
        }
    }

    /**
     * 取消所有订阅（用于测试或重置）。
     */
    public void unsubscribeAll() {
        subscriptions.clear();
        taskListeners.clear();
    }

    /**
     * 通知所有监听器和订阅者。
     */
    private void notifyListeners(EventType eventType, Task task, TaskStatus oldStatus, TaskStatus newStatus) {
        TaskEvent event = new TaskEvent(eventType, task, oldStatus, newStatus, null);

        // 通过全局 HookChain 触发任务生命周期 Hook（TASK_CREATED / TASK_COMPLETED）
        fireTaskHooks(eventType, task);

        // 通知旧版监听器（向后兼容）
        for (Consumer<TaskEvent> listener : taskListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                logger.warn("TaskListener error: " + e.getMessage());
            }
        }

        // 通知新版订阅者（支持过滤）
        for (Subscription sub : subscriptions) {
            try {
                if (sub.matches(event)) {
                    sub.dispatch(event);
                }
            } catch (Exception e) {
                logger.warn("TaskSubscription error [" + sub.getId() + "]: " + e.getMessage());
            }
        }
    }

    /**
     * 通过全局 HookChain 触发任务生命周期 Hook 事件。
     * <p>映射关系：</p>
     * <ul>
     *   <li>CREATED → TASK_CREATED</li>
     *   <li>COMPLETED → TASK_COMPLETED</li>
     * </ul>
     */
    private void fireTaskHooks(EventType eventType, Task task) {
        try {
            com.jwcode.core.hook.HookChain hookChain = com.jwcode.core.hook.HookChain.getGlobalInstance();
            if (hookChain == null || task == null) return;

            com.jwcode.core.hook.HookEventType hookEvent = switch (eventType) {
                case CREATED -> com.jwcode.core.hook.HookEventType.TASK_CREATED;
                case COMPLETED -> com.jwcode.core.hook.HookEventType.TASK_COMPLETED;
                default -> null;
            };
            if (hookEvent == null) return;

            com.jwcode.core.hook.HookContext context = new com.jwcode.core.hook.HookContext.Builder(hookEvent)
                .sessionId(null)
                .agentName("system")
                .taskId(task.getId())
                .metadata(Map.of(
                    "taskTitle", task.getTitle() != null ? task.getTitle() : "",
                    "taskStatus", task.getStatus() != null ? task.getStatus().name() : "",
                    "taskPriority", task.getPriority()
                ))
                .build();

            hookChain.execute(context);
            logger.debug("[TaskStore] Fired hook: {} for task {}", hookEvent, task.getId());
        } catch (Exception e) {
            logger.warn("[TaskStore] Hook event failed: {}", e.getMessage());
        }
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

        // 通知监听器（CREATED 事件）
        notifyListeners(EventType.CREATED, task, null, task.getStatus());

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

        Task oldTask = tasks.get(task.getId());
        if (oldTask == null) {
            logger.warn("Task not found for update: {}", task.getId());
            return null;
        }

        TaskStatus oldStatus = oldTask.getStatus();
        task.setUpdatedAt(java.time.LocalDateTime.now());
        tasks.put(task.getId(), task);
        logger.debug("Task updated: {}", task.getId());

        if (autoSaveEnabled) {
            save();
        }

        // 通知监听器
        EventType eventType = EventType.UPDATED;
        // 如果状态发生变化，额外触发 STATUS_CHANGED 和可能的 COMPLETED
        if (oldStatus != task.getStatus()) {
            notifyListeners(EventType.STATUS_CHANGED, task, oldStatus, task.getStatus());
            if (task.getStatus() == TaskStatus.COMPLETED) {
                notifyListeners(EventType.COMPLETED, task, oldStatus, task.getStatus());
            }
        }
        notifyListeners(eventType, task, oldStatus, task.getStatus());

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
            // 通知监听器（DELETED 事件）
            notifyListeners(EventType.DELETED, removed, removed.getStatus(), null);
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
