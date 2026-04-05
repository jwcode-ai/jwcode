package com.jwcode.core.planner;

/**
 * 计划模板接口
 */
public interface PlanTemplate {
    
    /**
     * 获取模板名称
     */
    String getName();
    
    /**
     * 获取模板描述
     */
    String getDescription();
    
    /**
     * 应用模板生成执行计划
     */
    ExecutionPlan apply(String userRequest, PlanningContext context);
}
