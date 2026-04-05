package com.jwcode.core.planner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 计划模板注册表
 */
public class PlanTemplateRegistry {
    
    private static final Map<String, PlanTemplate> templates = new ConcurrentHashMap<>();
    
    static {
        // 注册默认模板
        register(new CreateFeatureTemplate());
        register(new FixBugTemplate());
        register(new CodeReviewTemplate());
    }
    
    /**
     * 注册模板
     */
    public static void register(PlanTemplate template) {
        templates.put(template.getName(), template);
    }
    
    /**
     * 获取模板
     */
    public static PlanTemplate get(String name) {
        return templates.get(name);
    }
    
    /**
     * 获取所有模板名称
     */
    public static java.util.Collection<String> getTemplateNames() {
        return templates.keySet();
    }
    
    // ==================== 内置模板 ====================
    
    /**
     * 创建功能模板
     */
    static class CreateFeatureTemplate implements PlanTemplate {
        @Override
        public String getName() {
            return "create-feature";
        }
        
        @Override
        public String getDescription() {
            return "创建新功能的标准流程";
        }
        
        @Override
        public ExecutionPlan apply(String userRequest, PlanningContext context) {
            // 简化实现，返回预定义的计划
            return ExecutionPlan.builder()
                .planId("template_create_" + System.currentTimeMillis())
                .originalRequest(userRequest)
                .intent(IntentAnalysis.builder()
                    .type(IntentAnalysis.IntentType.CREATE)
                    .confidence(0.9)
                    .build())
                .steps(java.util.List.of(
                    PlanStep.builder()
                        .stepNumber(1)
                        .action("需求分析")
                        .description("分析功能需求和技术约束")
                        .agentType("analyzer")
                        .build(),
                    PlanStep.builder()
                        .stepNumber(2)
                        .action("架构设计")
                        .description("设计功能架构和接口")
                        .agentType("architect")
                        .dependsOnPrevious(true)
                        .build(),
                    PlanStep.builder()
                        .stepNumber(3)
                        .action("代码实现")
                        .description("编写功能代码")
                        .agentType("coder")
                        .dependsOnPrevious(true)
                        .build(),
                    PlanStep.builder()
                        .stepNumber(4)
                        .action("单元测试")
                        .description("编写并运行单元测试")
                        .agentType("test")
                        .dependsOnPrevious(true)
                        .build()
                ))
                .build();
        }
    }
    
    /**
     * 修复 Bug 模板
     */
    static class FixBugTemplate implements PlanTemplate {
        @Override
        public String getName() {
            return "fix-bug";
        }
        
        @Override
        public String getDescription() {
            return "修复 Bug 的标准流程";
        }
        
        @Override
        public ExecutionPlan apply(String userRequest, PlanningContext context) {
            return ExecutionPlan.builder()
                .planId("template_fix_" + System.currentTimeMillis())
                .originalRequest(userRequest)
                .intent(IntentAnalysis.builder()
                    .type(IntentAnalysis.IntentType.DEBUG)
                    .confidence(0.9)
                    .build())
                .steps(java.util.List.of(
                    PlanStep.builder()
                        .stepNumber(1)
                        .action("复现问题")
                        .description("尝试复现报告的问题")
                        .agentType("debug")
                        .build(),
                    PlanStep.builder()
                        .stepNumber(2)
                        .action("定位根因")
                        .description("分析并定位问题根因")
                        .agentType("debug")
                        .dependsOnPrevious(true)
                        .build(),
                    PlanStep.builder()
                        .stepNumber(3)
                        .action("实施修复")
                        .description("编写并应用修复代码")
                        .agentType("coder")
                        .dependsOnPrevious(true)
                        .build(),
                    PlanStep.builder()
                        .stepNumber(4)
                        .action("验证修复")
                        .description("验证问题已解决且未引入新问题")
                        .agentType("test")
                        .dependsOnPrevious(true)
                        .build()
                ))
                .build();
        }
    }
    
    /**
     * 代码审查模板
     */
    static class CodeReviewTemplate implements PlanTemplate {
        @Override
        public String getName() {
            return "code-review";
        }
        
        @Override
        public String getDescription() {
            return "代码审查的标准流程";
        }
        
        @Override
        public ExecutionPlan apply(String userRequest, PlanningContext context) {
            return ExecutionPlan.builder()
                .planId("template_review_" + System.currentTimeMillis())
                .originalRequest(userRequest)
                .intent(IntentAnalysis.builder()
                    .type(IntentAnalysis.IntentType.ANALYZE)
                    .confidence(0.85)
                    .build())
                .steps(java.util.List.of(
                    PlanStep.builder()
                        .stepNumber(1)
                        .action("代码扫描")
                        .description("扫描代码结构和潜在问题")
                        .agentType("analyzer")
                        .build(),
                    PlanStep.builder()
                        .stepNumber(2)
                        .action("规范检查")
                        .description("检查代码规范和最佳实践")
                        .agentType("reviewer")
                        .build(),
                    PlanStep.builder()
                        .stepNumber(3)
                        .action("安全审计")
                        .description("检查安全漏洞")
                        .agentType("reviewer")
                        .build(),
                    PlanStep.builder()
                        .stepNumber(4)
                        .action("生成报告")
                        .description("汇总审查结果")
                        .agentType("reviewer")
                        .dependsOnPrevious(true)
                        .build()
                ))
                .build();
        }
    }
}
