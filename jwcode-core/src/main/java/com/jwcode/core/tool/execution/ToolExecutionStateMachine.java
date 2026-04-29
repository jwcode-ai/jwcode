package com.jwcode.core.tool.execution;

import com.jwcode.core.tool.ToolProgress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.function.Consumer;

/**
 * 工具执行状态机
 * 
 * 实现状态转移规则：
 * | 当前状态 | 触发条件 | 下一状态 | 动作 |
 * | PARSE | JSON 非法 | CORRECTION | 向 LLM 返回："JSON 解析失败，请检查转义字符" |
 * | VALIDATE | 字段缺失/类型错 | CORRECTION | 返回正例模板 |
 * | EXECUTE | 超时/退出码非0 | CORRECTION | 返回错误摘要+建议 |
 * | CORRECTION | 已尝试 2 次 | FAILED | 标记失败，不再给 LLM 第三次机会 |
 * | REPORT | 成功 | DONE | 结果写入 ToolResultMemory |
 */
public class ToolExecutionStateMachine {
    
    private static final Logger logger = Logger.getLogger(ToolExecutionStateMachine.class.getName());
    
    /** 最大纠错次数 */
    private static final int MAX_CORRECTION = 2;
    
    /** 当前状态 */
    private volatile ToolExecutionState currentState = ToolExecutionState.PARSE;
    
    /** 纠错计数器 */
    private final AtomicInteger correctionAttempts = new AtomicInteger(0);
    
    /** 工具名称（用于日志） */
    private final String toolName;
    
    /** 进度回调 */
    private final Consumer<ToolProgress<?>> onProgress;
    
    /** 最后错误消息 */
    private volatile String lastErrorMessage;
    
    /** 最后错误类型 */
    private volatile ErrorType lastErrorType;
    
    public ToolExecutionStateMachine(String toolName, Consumer<ToolProgress<?>> onProgress) {
        this.toolName = toolName;
        this.onProgress = onProgress;
    }
    
    /**
     * 错误类型
     */
    public enum ErrorType {
        PARSE_ERROR,      // JSON 解析失败
        VALIDATE_ERROR,  // 字段缺失/类型错误
        EXECUTE_ERROR,   // 执行超时/退出码非0
        UNKNOWN_ERROR   // 未知错误
    }
    
    /**
     * 获取当前状态
     */
    public ToolExecutionState getCurrentState() {
        return currentState;
    }
    
    /**
     * 获取纠错尝试次数
     */
    public int getCorrectionAttempts() {
        return correctionAttempts.get();
    }
    
    /**
     * 是否可以继续纠错
     */
    public boolean canCorrect() {
        return correctionAttempts.get() < MAX_CORRECTION;
    }
    
    /**
     * 状态转移触发
     */
    public void transition(ErrorType errorType, String errorMessage) {
        this.lastErrorType = errorType;
        this.lastErrorMessage = errorMessage;
        
        switch (currentState) {
            case PARSE:
                if (errorType == ErrorType.PARSE_ERROR) {
                    transitionToCorrection("JSON 解析失败，请检查转义字符（如 Windows 路径 C:\\\\Users）");
                }
                break;
                
            case VALIDATE:
                if (errorType == ErrorType.VALIDATE_ERROR) {
                    // 返回正例模板
                    String template = getValidationTemplate();
                    transitionToCorrection(template);
                }
                break;
                
            case EXECUTE:
                if (errorType == ErrorType.EXECUTE_ERROR) {
                    // 返回错误摘要+建议
                    String suggestion = getExecutionSuggestion(errorMessage);
                    transitionToCorrection(suggestion);
                }
                break;
                
            case CORRECTION:
                if (!canCorrect()) {
                    transitionToFailed("已达到最大纠错次数：" + MAX_CORRECTION);
                }
                break;
                
            default:
                break;
        }
    }
    
    /**
     * 转移报告成功
     */
    public void reportSuccess() {
        currentState = ToolExecutionState.REPORT;
        logger.fine("[ToolStateMachine] " + toolName + " -> REPORT");
    }
    
    /**
     * 转移完成
     */
    public void complete() {
        currentState = ToolExecutionState.DONE;
        logger.fine("[ToolStateMachine] " + toolName + " -> DONE");
    }
    
    /**
     * 转移失败
     */
    public void fail(String errorMessage) {
        this.lastErrorMessage = errorMessage;
        currentState = ToolExecutionState.FAILED;
        logger.warning("[ToolStateMachine] " + toolName + " FAILED | error=" + errorMessage);
    }
    
    /**
     * 是否已完成（成功或失败）
     */
    public boolean isTerminal() {
        return currentState == ToolExecutionState.DONE || currentState == ToolExecutionState.FAILED;
    }
    
    /**
     * 获取给 LLM 的纠错提示
     */
    public String getCorrectionPrompt() {
        if (lastErrorType == null || lastErrorMessage == null) {
            return "工具执行遇到错误，请修正后重试。";
        }
        
        return switch (lastErrorType) {
            case PARSE_ERROR -> "JSON 解析失败: " + lastErrorMessage + "。请检查转义字符（Windows 路径使用双反斜杠 C:\\\\Users）。";
            case VALIDATE_ERROR -> "参数校验失败: " + lastErrorMessage + "。请参考以下正确格式：";
            case EXECUTE_ERROR -> "命令执行失败: " + lastErrorMessage + "。";
            default -> "执行错误: " + lastErrorMessage;
        };
    }
    
    // ==================== 私有方法 ====================
    
    private void transitionToCorrection(String message) {
        int attempt = correctionAttempts.incrementAndGet();
        currentState = ToolExecutionState.CORRECTION;
        logger.info("[ToolStateMachine] " + toolName + " -> CORRECTION (attempt " + attempt + "/" + MAX_CORRECTION + ")");
        
        if (onProgress != null) {
            onProgress.accept(ToolProgress.withMessage("state_change", 
                "进入纠错循环 " + attempt + "/" + MAX_CORRECTION + ": " + message));
        }
    }
    
    private void transitionToFailed(String message) {
        currentState = ToolExecutionState.FAILED;
        this.lastErrorMessage = message;
        logger.warning("[ToolStateMachine] " + toolName + " -> FAILED | " + message);
        
        if (onProgress != null) {
            onProgress.accept(ToolProgress.withMessage("state_change", 
                "工具执行失败: " + message));
        }
    }
    
    private String getValidationTemplate() {
        // 返回正例模板，帮助 LLM 修正参数
        // 这是你文档中提到的「正例模板」
        return """
            参数格式不正确。请使用以下正确格式：
            {
                "action": "execute",
                "tasks": [
                    {"name": "task1", "role": "coder", "task": "编写功能"}
                ]
            }
            注意：tasks 是数组（复数），不是 task（单数）。
            """;
    }
    
    private String getExecutionSuggestion(String errorMessage) {
        if (errorMessage != null) {
            if (errorMessage.contains("&&") || errorMessage.contains("||")) {
                return "PowerShell 中 && 无效，请用 ; 或 -and。或者拆分为两个独立的工具调用。";
            }
            if (errorMessage.contains("timeout") || errorMessage.contains("超时")) {
                return "命令执行超时，请减少搜索范围或增加超时时间。";
            }
            if (errorMessage.contains("exit code") || errorMessage.contains("退出码")) {
                return "命令返回非零退出码，请检查命令语法是否正确。";
            }
        }
        return "请检查命令语法后重试。";
    }
}