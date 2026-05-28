package com.jwcode.core.agent;

import com.jwcode.core.session.Session;
import com.jwcode.core.session.SessionFork;
import com.jwcode.core.task.ActiveTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 子任务拆分器
 * 
 * <p>实现预算耗尽时的任务拆分机制：
 * - 将复杂任务分解为多个子任务
 * - 为每个子任务 fork 独立会话
 * - 维护父子任务关系
 * 
 * <p>核心职责：
 * - 分析任务复杂度，确定是否需要拆分
 * - 智能分解任务为可管理的子任务
 * - 管理子任务会话的生命周期
 * 
 * @author JWCode Team
 * @since 2.0.0
 */
public class SubTaskSplitter {

    private static final Logger logger = LoggerFactory.getLogger(SubTaskSplitter.class);

    // ==================== 配置 ====================

    /** 每个子任务的最大 Token 预算 */
    private final int maxTokensPerSubTask;
    
    /** 最大子任务数量 */
    private final int maxSubTaskCount;

    // ==================== 状态 ====================

    /** 父子任务映射 */
    private final Map<String, TaskHierarchy> taskHierarchyMap;
    
    /** 活跃子任务会话 */
    private final Map<String, List<String>> activeSubSessions;

    // ==================== 构造函数 ====================

    public SubTaskSplitter() {
        this(50_000, 10); // 默认：每子任务 50K tokens，最多 10 个
    }

    public SubTaskSplitter(int maxTokensPerSubTask, int maxSubTaskCount) {
        this.maxTokensPerSubTask = maxTokensPerSubTask;
        this.maxSubTaskCount = maxSubTaskCount;
        this.taskHierarchyMap = new HashMap<>();
        this.activeSubSessions = new HashMap<>();
    }

    // ==================== 核心方法 ====================

    /**
     * 拆分并 fork 子任务
     * 
     * @param parentSession 父会话
     * @return 子会话列表
     */
    public List<Session> splitAndFork(Session parentSession) {
        ActiveTask currentTask = parentSession.getActiveTask();
        if (currentTask == null) {
            logger.warn("[SubTaskSplitter] 父会话没有活跃任务，无法拆分");
            return Collections.emptyList();
        }

        return splitAndFork(parentSession, currentTask);
    }

    /**
     * 拆分并 fork 子任务
     * 
     * @param parentSession 父会话
     * @param task 要拆分的任务
     * @return 子会话列表
     */
    public List<Session> splitAndFork(Session parentSession, ActiveTask task) {
        logger.info("[SubTaskSplitter] 开始拆分任务 | taskId={} | description={}", 
            task.getTaskId(), task.getDescription());

        // 分析任务并生成子任务描述
        List<SubTaskDescription> subTaskDescriptions = analyzeAndSplit(task);
        
        if (subTaskDescriptions.isEmpty()) {
            logger.warn("[SubTaskSplitter] 未能生成子任务");
            return Collections.emptyList();
        }

        // 创建子任务层级
        TaskHierarchy hierarchy = new TaskHierarchy(
            parentSession.getId(),
            task.getTaskId(),
            subTaskDescriptions.stream().map(SubTaskDescription::id).toList()
        );
        taskHierarchyMap.put(task.getTaskId(), hierarchy);

        // Fork 子会话
        List<Session> subSessions = new ArrayList<>();
        for (SubTaskDescription subTask : subTaskDescriptions) {
            Session subSession = parentSession.fork(subTask.reason());
            subSession.setMetadata("subTaskId", subTask.id());
            subSession.setMetadata("parentTaskId", task.getTaskId());
            subSession.setMetadata("subTaskIndex", subSessions.size());
            
            subSessions.add(subSession);
            logger.info("[SubTaskSplitter] 创建子会话 | subTaskId={} | reason={}", 
                subTask.id(), subTask.reason());
        }

        // 记录活跃子会话
        activeSubSessions.put(task.getTaskId(), 
            subSessions.stream().map(Session::getId).toList());

        logger.info("[SubTaskSplitter] 拆分完成 | 生成 {} 个子任务", subSessions.size());
        return subSessions;
    }

    /**
     * 分析任务并生成子任务描述
     */
    private List<SubTaskDescription> analyzeAndSplit(ActiveTask task) {
        String description = task.getDescription();
        
        // 启发式拆分策略（方法内无并发访问，使用 ArrayList 避免 CopyOnWriteArrayList 的不必要开销）
        List<SubTaskDescription> subTasks = new ArrayList<>();
        int subTaskCount = 0;

        // 检查是否有多步骤
        if (task.getSteps() != null && !task.getSteps().isEmpty()) {
            // 按已有步骤拆分
            int currentIndex = task.getCurrentStepIndex();
            if (currentIndex >= 0 && currentIndex < task.getSteps().size()) {
                // 已完成的步骤合并为一个子任务
                if (currentIndex > 0) {
                    String completedSummary = buildCompletedSummary(task, currentIndex);
                    subTasks.add(new SubTaskDescription(
                        UUID.randomUUID().toString(),
                        completedSummary,
                        "已完成部分总结",
                        subTaskCount++
                    ));
                }

                // 剩余步骤按组拆分
                List<String> remainingDescriptions = task.getSteps().stream()
                    .skip(currentIndex + 1)
                    .map(s -> s.getDescription())
                    .toList();

                subTasks.addAll(splitByGroups(remainingDescriptions, subTaskCount));
            }
        } else {
            // 无步骤信息，使用自然语言拆分
            subTasks.addAll(splitByHeuristics(description, subTaskCount));
        }

        // 限制子任务数量
        if (subTasks.size() > maxSubTaskCount) {
            logger.info("[SubTaskSplitter] 子任务数量超过限制，合并为 {} 个", maxSubTaskCount);
            return mergeSubTasks(subTasks, maxSubTaskCount);
        }

        return subTasks;
    }

    /**
     * 按组拆分
     */
    private List<SubTaskDescription> splitByGroups(List<String> descriptions, int startIndex) {
        List<SubTaskDescription> result = new ArrayList<>();
        int groupSize = Math.max(1, descriptions.size() / maxSubTaskCount);
        
        for (int i = 0; i < descriptions.size(); i += groupSize) {
            int end = Math.min(i + groupSize, descriptions.size());
            List<String> group = descriptions.subList(i, end);
            String combined = String.join("; ", group);
            
            result.add(new SubTaskDescription(
                UUID.randomUUID().toString(),
                combined,
                "第 " + (startIndex + result.size() + 1) + " 组任务",
                startIndex + result.size()
            ));
        }
        
        return result;
    }

    /**
     * 使用启发式拆分
     */
    private List<SubTaskDescription> splitByHeuristics(String description, int startIndex) {
        List<SubTaskDescription> result = new ArrayList<>();
        
        // 关键词拆分
        String[] keywords = {"先", "然后", "接着", "最后", "第一步", "第二步", "第三步", "首先", "其次", "最后"};
        
        String lowerDesc = description.toLowerCase();
        List<int[]> segments = new ArrayList<>();
        int lastStart = 0;
        
        for (int i = 0; i < description.length(); i++) {
            for (String keyword : keywords) {
                if (lowerDesc.regionMatches(true, i, keyword, 0, keyword.length())) {
                    if (i > lastStart) {
                        segments.add(new int[]{lastStart, i});
                    }
                    lastStart = i;
                    break;
                }
            }
        }
        
        if (segments.isEmpty()) {
            // 无法拆分，返回原始任务
            result.add(new SubTaskDescription(
                UUID.randomUUID().toString(),
                description,
                "独立任务",
                startIndex
            ));
            return result;
        }

        // 创建子任务描述
        for (int i = 0; i < segments.size(); i++) {
            int[] segment = segments.get(i);
            String subDesc = description.substring(segment[0], segment[1]).trim();
            if (!subDesc.isEmpty()) {
                result.add(new SubTaskDescription(
                    UUID.randomUUID().toString(),
                    subDesc,
                    "部分 " + (i + 1),
                    startIndex + i
                ));
            }
        }

        return result;
    }

    /**
     * 构建已完成部分总结
     */
    private String buildCompletedSummary(ActiveTask task, int currentIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("已完成 ").append(currentIndex).append(" 个步骤：\n");
        
        for (int i = 0; i < currentIndex && i < task.getSteps().size(); i++) {
            var step = task.getSteps().get(i);
            String status = step.getStatus().name();
            String result = step.getResult() != null ? step.getResult() : step.getError();
            sb.append("- ").append(step.getDescription());
            if (result != null) {
                sb.append(" → ").append(result);
            }
            sb.append(" [").append(status).append("]\n");
        }
        
        return sb.toString();
    }

    /**
     * 合并子任务
     */
    private List<SubTaskDescription> mergeSubTasks(List<SubTaskDescription> subTasks, int targetCount) {
        if (subTasks.size() <= targetCount) {
            return subTasks;
        }

        List<SubTaskDescription> result = new ArrayList<>();
        int mergeSize = (subTasks.size() + targetCount - 1) / targetCount;
        
        for (int i = 0; i < subTasks.size(); i += mergeSize) {
            int end = Math.min(i + mergeSize, subTasks.size());
            List<SubTaskDescription> group = subTasks.subList(i, end);
            
            String combined = group.stream()
                .map(SubTaskDescription::description)
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
            
            result.add(new SubTaskDescription(
                UUID.randomUUID().toString(),
                combined,
                "合并任务组 " + (result.size() + 1),
                result.size()
            ));
        }
        
        return result;
    }

    // ==================== 查询方法 ====================

    /**
     * 获取任务层级
     */
    public Optional<TaskHierarchy> getTaskHierarchy(String taskId) {
        return Optional.ofNullable(taskHierarchyMap.get(taskId));
    }

    /**
     * 获取父任务 ID
     */
    public Optional<String> getParentTaskId(String taskId) {
        return taskHierarchyMap.values().stream()
            .filter(h -> h.subTaskIds().contains(taskId))
            .map(h -> h.parentTaskId())
            .findFirst();
    }

    /**
     * 获取活跃子会话
     */
    public List<String> getActiveSubSessions(String parentTaskId) {
        return activeSubSessions.getOrDefault(parentTaskId, Collections.emptyList());
    }

    /**
     * 检查是否为子任务会话
     */
    public boolean isSubSession(String sessionId) {
        return activeSubSessions.values().stream()
            .anyMatch(list -> list.contains(sessionId));
    }

    /**
     * 清理会话记录
     */
    public void cleanupSession(String sessionId) {
        for (Map.Entry<String, List<String>> entry : activeSubSessions.entrySet()) {
            // 使用迭代器安全删除，避免 ConcurrentModificationException
            java.util.Iterator<String> iter = entry.getValue().iterator();
            while (iter.hasNext()) {
                if (sessionId.equals(iter.next())) {
                    iter.remove();
                }
            }
        }
    }

    // ==================== 内部类 ====================

    /**
     * 子任务描述
     */
    public record SubTaskDescription(
        String id,
        String description,
        String reason,
        int index
    ) {}

    /**
     * 任务层级
     */
    public record TaskHierarchy(
        String parentSessionId,
        String parentTaskId,
        List<String> subTaskIds
    ) {}

    // ==================== 配置方法 ====================

    public int getMaxTokensPerSubTask() {
        return maxTokensPerSubTask;
    }

    public int getMaxSubTaskCount() {
        return maxSubTaskCount;
    }

    @Override
    public String toString() {
        return String.format("SubTaskSplitter{maxTokens=%d, maxCount=%d, activeTasks=%d}",
            maxTokensPerSubTask, maxSubTaskCount, taskHierarchyMap.size());
    }
}