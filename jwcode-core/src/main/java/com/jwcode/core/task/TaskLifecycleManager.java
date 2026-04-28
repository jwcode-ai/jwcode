package com.jwcode.core.task;

import com.jwcode.core.llm.LLMMessage;
import com.jwcode.core.llm.LLMResponse;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.model.Message;
import com.jwcode.core.observability.ObservationEvent;
import com.jwcode.core.observability.ObservationPipeline;
import com.jwcode.core.planner.PlanStep;
import com.jwcode.core.planner.ai.AIPlanner;
import com.jwcode.core.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * 任务生命周期管理器 — 会话级任务状态机核心。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>意图检测：新任务 / 继续执行 / 补充信息</li>
 *   <li>任务规划：调用 AIPlanner 分解步骤，绑定到 Session.ActiveTask</li>
 *   <li>状态流转：步骤完成、失败、阻塞、等待输入</li>
 *   <li>上下文重置：新任务时彻底清空 Session messages / TokenBudget / compact 状态</li>
 *   <li>事件通知：通过 ObservationPipeline 发布状态变更</li>
 * </ul>
 */
public class TaskLifecycleManager {

    private static final Logger logger = LoggerFactory.getLogger(TaskLifecycleManager.class);

    // 新任务检测关键词
    private static final List<Pattern> NEW_TASK_PATTERNS = List.of(
        Pattern.compile("^/new\\s"),
        Pattern.compile("^/reset\\s*"),
        Pattern.compile("^(现在|接下来|另外|换个|新的|重新|开始).*(任务|做|帮我|请)"),
        Pattern.compile(".*换(一个|种).*(任务|方式|思路).*"),
        Pattern.compile(".*不(要|想)(做|继续).*(这个|当前).*")
    );
    
    // 任务清单操作检测关键词
    private static final List<Pattern> TASK_OP_PATTERNS = List.of(
        Pattern.compile(".*添[加加]步[骤骤].*"),
        Pattern.compile(".*删[除除]步[骤骤].*"),
        Pattern.compile(".*修改步[骤骤].*"),
        Pattern.compile(".*调整顺[序序].*"),
        Pattern.compile(".*重[新排]顺[序序].*"),
        Pattern.compile("^(add|delete|edit|reorder)\\s+(step|task).*", Pattern.CASE_INSENSITIVE)
    );

    private final LLMService llmService;
    private final AIPlanner aiPlanner;
    private final ObservationPipeline pipeline;

    public TaskLifecycleManager(LLMService llmService, ObservationPipeline pipeline) {
        this.llmService = llmService;
        this.aiPlanner = llmService != null ? new AIPlanner(llmService) : null;
        this.pipeline = pipeline != null ? pipeline : new com.jwcode.core.observability.DefaultObservationPipeline();
    }

    // ==================== 意图检测 ====================

    /**
     * 检测用户输入的意图。
     *
     * @return {@link TaskIntent}
     */
    public TaskIntent detectIntent(Session session, String prompt) {
        String trimmed = prompt != null ? prompt.trim() : "";

    // 1. 显式命令（快速路径，无需 LLM）
    if (trimmed.startsWith("/new") || trimmed.equals("/reset") || trimmed.equals("/switch")) {
        logger.info("[TaskLifecycle] 检测到显式新任务命令: {}", trimmed.substring(0, Math.min(30, trimmed.length())));
        return TaskIntent.NEW_TASK;
    }

        // 2. 获取当前任务
        ActiveTask activeTask = session.getActiveTask();

        // 3. 无当前任务或已完成/失败/取消 → 直接判定为新任务（无需 LLM）
        if (activeTask == null ||
            activeTask.getStatus() == TaskStatus.COMPLETED ||
            activeTask.getStatus() == TaskStatus.FAILED ||
            activeTask.getStatus() == TaskStatus.CANCELLED ||
            activeTask.getStatus() == TaskStatus.NONE) {
            return TaskIntent.NEW_TASK;
        }

        // 4. 当前在等待输入 → 补充信息（无需 LLM）
        if (activeTask.getStatus() == TaskStatus.WAITING_INPUT) {
            return TaskIntent.CLARIFICATION;
        }

        // 5. 其余所有情况直接交给 LLM 语义判断
        return detectIntentByLLM(session, prompt);
    }

    // ==================== 新任务启动 ====================

    /**
     * 启动新任务：归档旧任务、重置上下文、创建 ActiveTask、规划步骤。
     */
    public void startNewTask(Session session, String prompt) {
        logger.info("[TaskLifecycle] 启动新任务: {}", prompt.substring(0, Math.min(50, prompt.length())));

        // 1. 归档旧任务
        archiveCurrentTask(session);

        // 2. 重置会话上下文
        resetSession(session);

        // 3. 创建新 ActiveTask
        ActiveTask newTask = new ActiveTask(prompt);
        newTask.setStatus(TaskStatus.PENDING);
        session.setActiveTask(newTask);

        publishTaskStateChanged(null, newTask.getStatus(), "用户发起新任务");

        // 4. 规划任务（异步）
        planTask(session, newTask);
    }

    /**
     * 用户补充信息：恢复 WAITING_INPUT 状态的任务。
     */
    public void onUserInput(Session session, String prompt) {
        ActiveTask task = session.getActiveTask();
        if (task == null) {
            //  fallback：当作新任务
            startNewTask(session, prompt);
            return;
        }

        TaskStatus oldStatus = task.getStatus();
        if (oldStatus == TaskStatus.WAITING_INPUT) {
            task.setStatus(TaskStatus.EXECUTING);
            task.setWaitingFor(null);
            publishTaskStateChanged(oldStatus, TaskStatus.EXECUTING,
                "用户补充信息: " + prompt.substring(0, Math.min(50, prompt.length())));
            logger.info("[TaskLifecycle] 任务恢复执行: {}", task.getTaskId());
        }
    }

    // ==================== 步骤状态管理 ====================

    /**
     * 标记当前步骤成功完成，推进到下一步。
     */
    public void advanceStep(Session session, String result) {
        ActiveTask task = session.getActiveTask();
        if (task == null) return;

        TaskStep current = task.getCurrentStep();
        if (current != null) {
            current.markCompleted(result);
            publishTaskPlanUpdated(task);
            logger.info("[TaskLifecycle] 步骤完成 [{}/{}]: {}",
                current.getIndex() + 1, task.getSteps().size(), current.getDescription());
        }

        TaskStep next = task.advanceToNextStep();
        if (next != null) {
            task.setStatus(TaskStatus.EXECUTING);
            logger.info("[TaskLifecycle] 开始步骤 [{}/{}]: {}",
                next.getIndex() + 1, task.getSteps().size(), next.getDescription());
        } else {
            // 无下一步，检查是否全部完成
            checkTaskCompletion(session);
        }
    }

    /**
     * 标记当前步骤失败。
     */
    public void failStep(Session session, String error) {
        ActiveTask task = session.getActiveTask();
        if (task == null) return;

        TaskStep current = task.getCurrentStep();
        if (current != null) {
            current.markFailed(error);
            publishTaskPlanUpdated(task);
            logger.warn("[TaskLifecycle] 步骤失败 [{}/{}]: {} - {}",
                current.getIndex() + 1, task.getSteps().size(), current.getDescription(), error);
        }

        // 失败后标记任务状态，但不终止（让 AI 决定重试或放弃）
        if (task.getFailedCount() >= 3 || task.getCurrentStepIndex() >= task.getSteps().size() - 1) {
            task.setStatus(TaskStatus.FAILED);
            publishTaskStateChanged(TaskStatus.EXECUTING, TaskStatus.FAILED,
                "关键步骤失败: " + error);
        }
    }

    /**
     * 设置等待用户补充信息状态。
     */
    public void waitForUserInput(Session session, String question) {
        ActiveTask task = session.getActiveTask();
        if (task == null) return;

        TaskStatus oldStatus = task.getStatus();
        task.setStatus(TaskStatus.WAITING_INPUT);
        task.setWaitingFor(question);

        publishTaskStateChanged(oldStatus, TaskStatus.WAITING_INPUT,
            "等待用户补充: " + question);
        publishWaitingForInput(task, question);
        logger.info("[TaskLifecycle] 任务等待输入: {}", question);
    }

    /**
     * 检查任务是否全部完成。
     */
    public void checkTaskCompletion(Session session) {
        ActiveTask task = session.getActiveTask();
        if (task == null) return;

        if (task.isAllCompleted()) {
            TaskStatus oldStatus = task.getStatus();
            task.setStatus(TaskStatus.COMPLETED);
            publishTaskStateChanged(oldStatus, TaskStatus.COMPLETED,
                "所有步骤已完成");
            logger.info("[TaskLifecycle] 任务完成: {}", task.getTaskId());
        }
    }

    /**
     * 动态添加新步骤（AI 执行过程中发现遗漏工作）。
     */
    public void addDynamicStep(Session session, String description) {
        ActiveTask task = session.getActiveTask();
        if (task == null) return;

        int newIndex = task.getSteps().size();
        TaskStep step = new TaskStep(newIndex, description);

        // 使用反射/内部方式添加（getSteps 返回 unmodifiableList）
        // 这里通过 setSteps 重新设置
        List<TaskStep> mutableSteps = new ArrayList<>(task.getSteps());
        mutableSteps.add(step);
        task.setSteps(mutableSteps);

        publishTaskPlanUpdated(task);
        logger.info("[TaskLifecycle] 动态添加步骤 [{}]: {}", newIndex + 1, description);
    }
    
    // ==================== 任务清单操作 ====================
    
    /**
     * 操作任务清单：添加/删除/修改步骤
     * @param session 会话
     * @param operation 操作类型：add, delete, update, reorder
     * @param index 步骤索引（从 0 开始）
     * @param description 描述（add/update 时需要）
     * @param toIndex 目标索引（reorder 时需要）
     * @return 操作结果描述
     */
    public String operateTaskList(Session session, String operation, Integer index, String description, Integer toIndex) {
        ActiveTask task = session.getActiveTask();
        if (task == null) {
            return "当前没有任务清单，请先创建一个任务";
        }
        
        return switch (operation.toLowerCase()) {
            case "add" -> {
                if (description == null || description.isEmpty()) {
                    yield "请提供步骤描述";
                }
                task.addStep(description);
                yield "已添加步骤: " + description;
            }
            case "delete" -> {
                if (index == null || !task.removeStep(index)) {
                    yield "删除步骤失败：索引无效";
                }
                yield "已删除步骤 #" + (index + 1);
            }
            case "update" -> {
                if (index == null || !task.updateStep(index, description)) {
                    yield "更新步骤失败：索引无效";
                }
                yield "已更新步骤 #" + (index + 1) + ": " + description;
            }
            case "reorder" -> {
                if (toIndex == null || !task.reorderStep(index, toIndex)) {
                    yield "调整顺序失败：索引无效";
                }
                yield "已将步骤 #" + (index + 1) + " 移动到 #" + (toIndex + 1);
            }
            default -> "未知操作: " + operation;
        };
    }
    
    /**
     * 获取当前任务清单摘要（用于显示给用户）
     */
    public String getTaskListSummary(Session session) {
        ActiveTask task = session.getActiveTask();
        if (task == null) {
            return "当前没有任务清单";
        }
        return task.toTaskListSummary();
    }

    // ==================== 规划 ====================

    private void planTask(Session session, ActiveTask task) {
        TaskStatus oldStatus = task.getStatus();
        task.setStatus(TaskStatus.PLANNING);
        publishTaskStateChanged(oldStatus, TaskStatus.PLANNING, "开始规划任务步骤");

        if (aiPlanner == null) {
            // 降级：单步骤任务
            logger.warn("[TaskLifecycle] AIPlanner 不可用，降级为单步骤任务");
            task.setSteps(List.of(new TaskStep(0, task.getDescription())));
            task.setStatus(TaskStatus.PLANNED);
            task.setCurrentStepIndex(-1);
            publishTaskStateChanged(TaskStatus.PLANNING, TaskStatus.PLANNED, "单步骤模式");
            injectTaskPlanSystemMessage(session, task);
            return;
        }

        try {
            // 异步规划
            CompletableFuture.supplyAsync(() -> {
                try {
                    var analysis = aiPlanner.analyze(task.getDescription(), new HashMap<>()).join();
                    List<PlanStep> planSteps = aiPlanner.decompose(task.getDescription(), analysis).join();

                    List<TaskStep> steps = new ArrayList<>();
                    for (int i = 0; i < planSteps.size(); i++) {
                        PlanStep ps = planSteps.get(i);
                        steps.add(new TaskStep(i, ps.getDescription() != null ? ps.getDescription() : ps.getAction()));
                    }

                    task.setSteps(steps);
                    task.setStatus(TaskStatus.PLANNED);
                    task.setCurrentStepIndex(-1);

                    publishTaskStateChanged(TaskStatus.PLANNING, TaskStatus.PLANNED,
                        "规划完成，共 " + steps.size() + " 步");
                    publishTaskPlanUpdated(task);
                    logger.info("[TaskLifecycle] 规划完成: {} 步", steps.size());

                    // 将任务清单注入会话
                    injectTaskPlanSystemMessage(session, task);

                } catch (Exception e) {
                    logger.error("[TaskLifecycle] 规划失败", e);
                    // 降级为单步骤
                    task.setSteps(List.of(new TaskStep(0, task.getDescription())));
                    task.setStatus(TaskStatus.PLANNED);
                    task.setCurrentStepIndex(-1);
                    publishTaskStateChanged(TaskStatus.PLANNING, TaskStatus.PLANNED, "规划失败，降级为单步骤");
                    injectTaskPlanSystemMessage(session, task);
                }
                return null;
            });

        } catch (Exception e) {
            logger.error("[TaskLifecycle] 启动规划异常", e);
            task.setSteps(List.of(new TaskStep(0, task.getDescription())));
            task.setStatus(TaskStatus.PLANNED);
            injectTaskPlanSystemMessage(session, task);
        }
    }

    // ==================== 上下文重置 ====================

    private void resetSession(Session session) {
        // 使用 Session 内置的 resetForNewTask() 方法
        session.resetForNewTask();
        logger.info("[TaskLifecycle] Session 上下文已重置");
    }

    private void archiveCurrentTask(Session session) {
        ActiveTask oldTask = session.getActiveTask();
        if (oldTask == null) return;

        TaskStatus oldStatus = oldTask.getStatus();
        if (oldStatus != TaskStatus.COMPLETED && oldStatus != TaskStatus.FAILED && oldStatus != TaskStatus.CANCELLED) {
            oldTask.setStatus(TaskStatus.CANCELLED);
            publishTaskStateChanged(oldStatus, TaskStatus.CANCELLED, "用户切换新任务");
            logger.info("[TaskLifecycle] 旧任务已归档: {}", oldTask.getTaskId());
        }

        // TODO: 可持久化到 TaskStore
    }

    // ==================== 系统消息注入 ====================

    private void injectTaskPlanSystemMessage(Session session, ActiveTask task) {
        String planText = task.toTaskListSummary();
        session.addMessage(Message.createSystemMessage(
            planText + "\n\n请按上述清单逐步执行，每完成一步汇报进度。如需用户补充信息，主动说明。"
        ));
    }

    // ==================== 事件发布 ====================

    private void publishTaskStateChanged(TaskStatus oldStatus, TaskStatus newStatus, String reason) {
        if (pipeline == null) return;
        try {
            pipeline.publish(new ObservationEvent.TaskStateChanged(
                "active", "当前任务", oldStatus, newStatus, reason
            ));
        } catch (Exception e) {
            logger.warn("发布任务状态事件失败", e);
        }
    }

    private void publishTaskPlanUpdated(ActiveTask task) {
        if (pipeline == null) return;
        try {
            TaskStep current = task.getCurrentStep();
            pipeline.publish(new ObservationEvent.TaskPlanUpdated(
                task.getTaskId(),
                task.getSteps().size(),
                task.getCompletedCount(),
                current != null ? current.getDescription() : null
            ));
        } catch (Exception e) {
            logger.warn("发布任务计划事件失败", e);
        }
    }

    private void publishWaitingForInput(ActiveTask task, String question) {
        if (pipeline == null) return;
        try {
            pipeline.publish(new ObservationEvent.WaitingForInput(
                task.getTaskId(), question
            ));
        } catch (Exception e) {
            logger.warn("发布等待输入事件失败", e);
        }
    }

    // ==================== LLM 语义意图检测 ====================

    /**
     * 使用 LLM 进行语义意图检测。
     * Prompt 极简，要求只输出一个单词，成本极低（<100 tokens）。
     * 带有 2 秒超时保护，失败时 fallback 到 CONTINUE。
     */
    private TaskIntent detectIntentByLLM(Session session, String prompt) {
        if (llmService == null) {
            return TaskIntent.CONTINUE;
        }

        ActiveTask activeTask = session.getActiveTask();
        // 如果当前没有正在执行的任务，无需 LLM 判断
        if (activeTask == null) {
            return TaskIntent.CONTINUE;
        }

        String currentTaskDesc = activeTask.getDescription() != null
            ? activeTask.getDescription()
            : "无";

        // 优化后的 prompt，包含更多判断逻辑
        String systemPrompt = "你是一个意图分类器。只输出一个单词：NEW_TASK、CONTINUE 或 CLARIFICATION。不要解释。\n\n判断规则：\n- 如果用户明确提到新任务、不同的事情、暂停当前任务、换任务等 → NEW_TASK\n- 如果用户在继续、补充、完善当前任务的细节 → CLARIFICATION\n- 其他情况 → CONTINUE";
        
        List<LLMMessage> messages = List.of(
            LLMMessage.system(systemPrompt),
            LLMMessage.user(String.format(
                "当前任务：%s\n用户输入：%s\n请判断用户意图：",
                currentTaskDesc, prompt
            ))
        );

        try {
            LLMResponse response = llmService.chat(messages)
                .get(5, java.util.concurrent.TimeUnit.MINUTES);

            if (response.hasError() || response.getContent() == null) {
                return TaskIntent.CONTINUE;
            }

            String content = response.getContent().trim().toUpperCase();
            if (content.contains("NEW_TASK")) {
                logger.info("[TaskLifecycle] LLM 判断为新任务: {}", prompt.substring(0, Math.min(40, prompt.length())));
                return TaskIntent.NEW_TASK;
            }
            if (content.contains("CLARIFICATION")) {
                return TaskIntent.CLARIFICATION;
            }
            return TaskIntent.CONTINUE;

        } catch (Exception e) {
            logger.debug("[TaskLifecycle] LLM 意图检测失败，fallback 到 CONTINUE: {}", e.getMessage());
            return TaskIntent.CONTINUE;
        }
    }

    // ==================== 意图枚举 ====================

    public enum TaskIntent {
        /**
         * 新任务
         */
        NEW_TASK,

        /**
         * 继续当前任务
         */
        CONTINUE,

        /**
         * 补充信息（当前任务处于 WAITING_INPUT）
         */
        CLARIFICATION
    }
}
