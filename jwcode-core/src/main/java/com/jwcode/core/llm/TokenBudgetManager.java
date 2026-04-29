package com.jwcode.core.llm;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.function.Consumer;

/**
 * Token 预算管理器 - 动态油门机制
 * 
 * 实现软限制 Token 消耗：
 * | 预算使用率 | 动作 |
 * | 50% | 正常执行 |
 * | 70% | 轻度告警→压缩历史 |
 * | 85% | 中度告警→禁用非必要工具 |
 * | 95% | 紧急模式→清空历史 |
 * | 100% | 硬熔断→停止接收新任务 |
 */
public class TokenBudgetManager {
    
    private static final Logger logger = Logger.getLogger(TokenBudgetManager.class.getName());
    
    /** 总预算 */
    private final long totalBudget;
    
    /** 预留预算（最终总结用，10%） */
    private final long reservedBudget;
    
    /** 当前已使用 */
    private final AtomicLong usedTokens = new AtomicLong(0);
    
    /** 进度回调 */
    private final Consumer<BudgetAlert> alertConsumer;
    
    /** 历史消息计数 */
    private int messageHistoryCount = 0;
    
    /** 告警级别 */
    private volatile AlertLevel currentAlertLevel = AlertLevel.NORMAL;
    
    /** 是否已熔断 */
    private volatile boolean isCircuitBroken = false;
    
    /**
     * 告警级别
     */
    public enum AlertLevel {
        NORMAL,      // 50% 以下
        LIGHT,       // 50-70%
        MODERATE,    // 70-85%
        HEAVY,      // 85-95%
        EMERGENCY,   // 95-100%
        BROKEN       // 100% 硬熔断
    }
    
    /**
     * 告警信息
     */
    public record BudgetAlert(
        AlertLevel level,
        long usedTokens,
        long totalBudget,
        double usagePercent,
        String action
    ) {
        public String toPrompt() {
            return switch (level) {
                case NORMAL -> null;
                case LIGHT -> "Token 使用已达 70%，开始压缩历史对话（保留最近 5 轮）";
                case MODERATE -> "Token 使用已达 85%，禁用非必要工具，切换轻量模型";
                case HEAVY -> "Token 使用已达 95%，紧急模式：清空历史，仅保留当前任务";
                case EMERGENCY -> "Token 即将耗尽（99%），请尽快完成当前任务";
                case BROKEN -> "Token 预算耗尽，请新开会话继续";
            };
        }
    }
    
    /**
     * 创建预算管理器
     * 
     * @param totalBudget 总 token 数（如 1_000_000）
     * @param alertConsumer 告警回调
     */
    public TokenBudgetManager(long totalBudget, Consumer<BudgetAlert> alertConsumer) {
        this.totalBudget = totalBudget;
        this.reservedBudget = totalBudget / 10;  // 10% 预留
        this.alertConsumer = alertConsumer;
        logger.info("[TokenBudget] 初始化预算: " + totalBudget + " tokens, 预留: " + reservedBudget);
    }
    
    /**
     * 创建默认预算管理器（100万 token）
     */
    public static TokenBudgetManager createDefault() {
        return new TokenBudgetManager(1_000_000, alert -> {
            logger.warning("[TokenBudget] " + alert.action());
        });
    }
    
    /**
     * 分配预算
     * 
     * @param phase 阶段（planning/execution/reserved）
     * @param percent 百分比
     * @return 该阶段可用预算
     */
    public long allocate(String phase, double percent) {
        long budget = (long) (totalBudget * percent);
        logger.info("[TokenBudget] 分配 " + phase + ": " + budget + " tokens (" + (percent * 100) + "%)");
        return budget;
    }
    
    /**
     * 预分配的阶段预算
     */
    public long getPlanningBudget() {
        return allocate("planning", 0.20);  // 20%
    }
    
    public long getExecutionBudget() {
        return allocate("execution", 0.60);  // 60%
    }
    
    public long getReservedBudget() {
        return reservedBudget;
    }
    
    /**
     * 使用 Token
     * 
     * @param tokens 使用数量
     * @return 是否成功（false 表示预算不足）
     */
    public boolean use(long tokens) {
        if (isCircuitBroken) {
            logger.warning("[TokenBudget] 预算已熔断，无法使用");
            return false;
        }
        
        long current = usedTokens.addAndGet(tokens);
        long available = totalBudget - reservedBudget;
        
        if (current > available) {
            // 超过可用预算，触发熔断
            triggerCircuitBreak();
            return false;
        }
        
        // 检查告警级别
        checkAlertLevel(current);
        return true;
    }
    
    /**
     * 检查并更新告警级别
     */
    private void checkAlertLevel(long current) {
        double usagePercent = (double) current / totalBudget * 100;
        AlertLevel newLevel;
        String action;
        
        if (usagePercent >= 95) {
            newLevel = AlertLevel.EMERGENCY;
            action = "紧急模式：清空历史，仅保留当前任务";
        } else if (usagePercent >= 85) {
            newLevel = AlertLevel.HEAVY;
            action = "中度告警：禁用非必要工具";
        } else if (usagePercent >= 70) {
            newLevel = AlertLevel.MODERATE;
            action = "轻度告警：压缩历史对话";
        } else if (usagePercent >= 50) {
            newLevel = AlertLevel.LIGHT;
            action = null;
        } else {
            newLevel = AlertLevel.NORMAL;
            action = null;
        }
        
        // 触发回调
        if (newLevel != currentAlertLevel && action != null) {
            currentAlertLevel = newLevel;
            BudgetAlert alert = new BudgetAlert(newLevel, current, totalBudget, usagePercent, action);
            logger.warning("[TokenBudget] " + alert.toPrompt());
            
            if (alertConsumer != null) {
                alertConsumer.accept(alert);
            }
        }
    }
    
    /**
     * 触发熔断
     */
    private void triggerCircuitBreak() {
        isCircuitBroken = true;
        currentAlertLevel = AlertLevel.BROKEN;
        BudgetAlert alert = new BudgetAlert(
            AlertLevel.BROKEN, 
            usedTokens.get(), 
            totalBudget, 
            100, 
            "Token 预算耗尽，请新开会话"
        );
        logger.severe("[TokenBudget] 硬熔断！已停止接收新任务");
        
        if (alertConsumer != null) {
            alertConsumer.accept(alert);
        }
    }
    
    /**
     * 获取当前使用量
     */
    public long getUsedTokens() {
        return usedTokens.get();
    }
    
    /**
     * 获取剩余预算
     */
    public long getRemainingTokens() {
        return totalBudget - usedTokens.get();
    }
    
    /**
     * 获取使用百分比
     */
    public double getUsagePercent() {
        return (double) usedTokens.get() / totalBudget * 100;
    }
    
    /**
     * 是否可以继续（未熔断）
     */
    public boolean canContinue() {
        return !isCircuitBroken;
    }
    
    /**
     * 获取当前告警级别
     */
    public AlertLevel getAlertLevel() {
        return currentAlertLevel;
    }
    
    /**
     * 压缩历史（保留最近 N 轮）
     * 
     * @param keepCount 保留数量
     * @return 释放的 token 估计值
     */
    public long compressHistory(int keepCount) {
        // 简单实现：记录需要保留的消息数
        int toRelease = messageHistoryCount - keepCount;
        if (toRelease > 0) {
            // 假设每条消息平均 1000 tokens
            long released = toRelease * 1000L;
            logger.info("[TokenBudget] 压缩历史：释放约 " + released + " tokens，保留最近 " + keepCount + " 轮");
            return released;
        }
        return 0;
    }
    
    /**
     * 设置消息历史计数
     */
    public void setMessageHistoryCount(int count) {
        this.messageHistoryCount = count;
    }
    
    /**
     * 构建提示词部分（让 LLM 知道预算状态）
     */
    public String getBudgetPrompt() {
        double percent = getUsagePercent();
        return """
            [预算状态]
            已使用: %d / %d tokens (%.1f%%)
            剩余: %d tokens
            状态: %s
            
            请 %s
            """.formatted(
                usedTokens.get(),
                totalBudget,
                percent,
                getRemainingTokens(),
                currentAlertLevel.name(),
                switch (currentAlertLevel) {
                    case NORMAL -> "正常执行";
                    case LIGHT -> "注意优化表达";
                    case MODERATE -> "压缩输出，保留要点";
                    case HEAVY -> "最小化输出，直接给出结果";
                    case EMERGENCY -> "立即完成当前任务，准备结束";
                    case BROKEN -> "会话即将结束";
                }
            );
    }
}