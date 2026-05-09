package com.jwcode.core.a2a.router;

import com.jwcode.core.a2a.model.AgentCard;
import com.jwcode.core.a2a.model.Skill;
import com.jwcode.core.a2a.registry.A2ARegistry;
import com.jwcode.core.a2a.registry.AgentSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * AgentRouter — Agent 路由选择器。
 *
 * <p>根据任务所需的 capability（skillId），从 A2ARegistry 中查找匹配的 Agent，
 * 并根据指定的路由策略选择最优的 Agent。</p>
 *
 * <p>支持策略：
 * <ul>
 *   <li>LEAST_LOAD — 负载最低优先</li>
 *   <li>BEST_MATCH — 能力匹配度最高优先</li>
 *   <li>ROUND_ROBIN — 轮询</li>
 *   <li>RANDOM — 随机</li>
 * </ul>
 * </p>
 */
public class AgentRouter {

    private static final Logger logger = Logger.getLogger(AgentRouter.class.getName());

    /** 轮询计数器（按 skillId 分组） */
    private final ConcurrentHashMap<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    /** 随机数生成器 */
    private final Random random = new Random();

    /**
     * 选择一个匹配的 Agent
     *
     * @param registry A2A Registry 实例
     * @param requiredCapability 所需的技能 ID
     * @param strategy 路由策略
     * @return 匹配的 AgentSession，如果没有可用 Agent 则返回 empty
     */
    public Optional<AgentSession> selectAgent(A2ARegistry registry,
                                               String requiredCapability,
                                               RoutingStrategy strategy) {
        if (requiredCapability == null || requiredCapability.isEmpty()) {
            // 没有指定技能要求，从所有可用 Agent 中选择
            return selectFromAll(registry, strategy);
        }

        // 查找具备该技能的可用 Agent
        List<AgentSession> candidates = registry.findBySkillId(requiredCapability);

        if (candidates.isEmpty()) {
            logger.fine("[AgentRouter] No available agents for skill: " + requiredCapability);
            return Optional.empty();
        }

        return selectByStrategy(candidates, requiredCapability, strategy);
    }

    /**
     * 从所有可用 Agent 中选择
     */
    private Optional<AgentSession> selectFromAll(A2ARegistry registry, RoutingStrategy strategy) {
        List<AgentSession> allAvailable = registry.getAvailableSessions();
        if (allAvailable.isEmpty()) {
            return Optional.empty();
        }
        return selectByStrategy(allAvailable, null, strategy);
    }

    /**
     * 根据策略从候选列表中选取一个 Agent
     */
    private Optional<AgentSession> selectByStrategy(List<AgentSession> candidates,
                                                     String skillId,
                                                     RoutingStrategy strategy) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }

        return switch (strategy) {
            case LEAST_LOAD -> selectLeastLoaded(candidates);
            case BEST_MATCH -> selectBestMatch(candidates, skillId);
            case ROUND_ROBIN -> selectRoundRobin(candidates, skillId);
            case RANDOM -> selectRandom(candidates);
            case FASTEST_RESPONSE -> selectLeastLoaded(candidates); // 降级为负载最低
        };
    }

    /**
     * 选择负载最低的 Agent
     */
    private Optional<AgentSession> selectLeastLoaded(List<AgentSession> candidates) {
        return candidates.stream()
            .min(Comparator.comparingDouble(AgentSession::getLoadRatio));
    }

    /**
     * 选择能力匹配度最高的 Agent
     * 匹配度基于 AgentCard 中技能的数量和描述匹配程度
     */
    private Optional<AgentSession> selectBestMatch(List<AgentSession> candidates, String skillId) {
        if (skillId == null) {
            return selectLeastLoaded(candidates);
        }

        // 按技能匹配度排序：拥有更多技能的 Agent 优先
        return candidates.stream()
            .max(Comparator.comparingInt(s -> {
                AgentCard card = s.getAgentCard();
                if (card == null || card.getSkills() == null) return 0;
                // 优先选择技能列表更丰富的 Agent
                return card.getSkills().size();
            }));
    }

    /**
     * 轮询选择
     */
    private Optional<AgentSession> selectRoundRobin(List<AgentSession> candidates, String skillId) {
        String key = skillId != null ? skillId : "__all__";
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
        int index = counter.getAndIncrement() % candidates.size();
        return Optional.of(candidates.get(index));
    }

    /**
     * 随机选择
     */
    private Optional<AgentSession> selectRandom(List<AgentSession> candidates) {
        int index = random.nextInt(candidates.size());
        return Optional.of(candidates.get(index));
    }
}
