package com.jwcode.core.a2a.dispatcher;

import com.jwcode.core.a2a.model.A2ATask;
import com.jwcode.core.a2a.model.AgentCard;
import com.jwcode.core.a2a.model.TaskOutput;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * AgentDispatcher — Agent 调度抽象接口。
 *
 * <p>定义 Orchestrator 调度子Agent 的统一契约。
 * 支持本地内存调度（LocalAgentDispatcher）和远程 A2A HTTP 调度（A2AAgentDispatcher）。</p>
 *
 * <p>Orchestrator 通过此接口与子Agent 交互，无需关心底层通信方式。</p>
 */
public interface AgentDispatcher {

    /**
     * 获取所有已注册的 Agent Card
     *
     * @return Agent Card 列表
     */
    List<AgentCard> getAvailableAgents();

    /**
     * 根据技能 ID 查找能处理该技能的 Agent
     *
     * @param skillId 技能 ID
     * @return 匹配的 Agent Card，未找到返回 null
     */
    AgentCard findAgentBySkill(String skillId);

    /**
     * 根据 Agent 类型查找 Agent
     *
     * @param agentType Agent 类型/角色
     * @return 匹配的 Agent Card，未找到返回 null
     */
    AgentCard findAgentByType(String agentType);

    /**
     * 提交任务给指定 Agent 执行（异步）
     *
     * @param agentName 目标 Agent 名称
     * @param task      待执行的任务
     * @return 异步任务结果
     */
    CompletableFuture<TaskOutput> submitTask(String agentName, A2ATask task);

    /**
     * 提交任务并等待结果（同步阻塞，使用默认超时）
     *
     * @param agentName 目标 Agent 名称
     * @param task      待执行的任务
     * @return 任务执行结果
     */
    TaskOutput submitTaskSync(String agentName, A2ATask task);

    /**
     * 提交任务并等待结果（同步阻塞，可指定超时）
     *
     * @param agentName 目标 Agent 名称
     * @param task      待执行的任务
     * @param timeout   超时时间
     * @param unit      超时时间单位
     * @return 任务执行结果
     */
    TaskOutput submitTaskSync(String agentName, A2ATask task, long timeout, TimeUnit unit);

    /**
     * 查询任务执行状态
     *
     * @param taskId 任务 ID
     * @return 当前任务（含最新状态），未找到返回 null
     */
    A2ATask getTaskStatus(String taskId);

    /**
     * 取消正在执行的任务
     *
     * @param taskId 任务 ID
     * @return 是否成功取消
     */
    boolean cancelTask(String taskId);

    /**
     * 判断调度器是否可用
     *
     * @return true 如果可用
     */
    boolean isAvailable();

    /**
     * 获取调度器名称
     *
     * @return 调度器名称
     */
    String getName();

    /**
     * 关闭调度器，释放资源
     */
    void shutdown();
}
