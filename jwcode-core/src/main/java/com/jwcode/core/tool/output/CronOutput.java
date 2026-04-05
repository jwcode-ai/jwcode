package com.jwcode.core.tool.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Cron 工具输出结果
 */
public record CronOutput(
    
    /** 操作是否成功 */
    @JsonProperty("success")
    boolean success,
    
    /** 任务 ID */
    @JsonProperty("id")
    String id,
    
    /** 任务列表（用于 list 操作） */
    @JsonProperty("jobs")
    List<CronJobInfo> jobs,
    
    /** 操作消息 */
    @JsonProperty("message")
    String message
) {
    public static CronOutput success(String id, String message) {
        return new CronOutput(true, id, null, message);
    }
    
    public static CronOutput list(List<CronJobInfo> jobs) {
        return new CronOutput(true, null, jobs, "当前有 " + jobs.size() + " 个定时任务");
    }
    
    public static CronOutput error(String message) {
        return new CronOutput(false, null, null, message);
    }
    
    /**
     * 定时任务信息
     */
    public record CronJobInfo(
        @JsonProperty("id")
        String id,
        
        @JsonProperty("cronExpression")
        String cronExpression,
        
        @JsonProperty("command")
        String command,
        
        @JsonProperty("description")
        String description
    ) {}
}