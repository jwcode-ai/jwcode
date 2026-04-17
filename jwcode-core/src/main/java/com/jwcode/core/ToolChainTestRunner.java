package com.jwcode.core;

import com.jwcode.core.coordinator.ToolTestConfig;
import com.jwcode.core.coordinator.ToolTestCoordinator;
import com.jwcode.core.report.TestReport;
import com.jwcode.core.report.MarkdownFormatter;
import com.jwcode.core.report.JsonFormatter;

/**
 * JwCode 工具链测试运行器
 * 
 * 演示如何使用优化后的工具测试框架
 */
public class ToolChainTestRunner {

    public static void main(String[] args) {
        System.out.println("""
            ╔═══════════════════════════════════════════════════════════════╗
            ║           JwCode 工具链测试框架 - 演示程序                   ║
            ╚═══════════════════════════════════════════════════════════════╝
            """);

        // 创建配置
        ToolTestConfig config = ToolTestConfig.builder()
            .testSuiteName("JwCode 工具链测试")
            .environmentCheckEnabled(true)
            .strictMode(false)
            .timeoutSeconds(30)
            .maxConsecutiveFailures(5)
            .includeToolsRequiringExternalDeps(false)
            .build();

        // 创建协调器
        ToolTestCoordinator coordinator = new ToolTestCoordinator(config);

        // 设置进度回调
        coordinator.runAllTests(progress -> {
            System.out.printf("\r[%d/%d] 测试中: %s", 
                progress.current(), progress.total(), progress.currentTool());
        });

        System.out.println("\n");

        // 获取报告
        TestReport report = coordinator.getStateMachine().getCurrentState().name().equals("PENDING") 
            ? TestReport.builder().testSuiteName("JwCode 工具链测试").build()
            : coordinator.getStateMachine().getCurrentState().name().equals("TERMINATED")
                ? TestReport.builder().testSuiteName("JwCode 工具链测试").build()
                : null;

        // 生成 Markdown 报告
        String markdownReport = new MarkdownFormatter().format(
            TestReport.builder()
                .testSuiteName("JwCode 工具链测试")
                .addWarning("这是演示报告")
                .build()
        );
        
        System.out.println("═══ Markdown 报告预览 ═══");
        System.out.println(markdownReport);

        // 生成 JSON 报告
        String jsonReport = new JsonFormatter().format(
            TestReport.builder()
                .testSuiteName("JwCode 工具链测试")
                .build()
        );
        
        System.out.println("═══ JSON 报告预览 ═══");
        System.out.println(jsonReport);

        System.out.println("""
            
            ╔═══════════════════════════════════════════════════════════════╗
            ║                    测试框架特性                              ║
            ╠═══════════════════════════════════════════════════════════════╣
            ║ ✅ 环境自检 - 自动检测依赖可用性                              ║
            ║ ✅ 参数校验 - 严格的输入参数验证                              ║
            ║ ✅ 流程控制 - 支持终止条件和部分通过                          ║
            ║ ✅ 异常处理 - 全面的异常捕获和恢复                            ║
            ║ ✅ 状态管理 - 明确的任务生命周期                              ║
            ║ ✅ 报告生成 - 支持 Markdown/JSON 格式                         ║
            ║ ✅ 进度追踪 - 实时测试进度可视化                              ║
            ╚═══════════════════════════════════════════════════════════════╝
            """);
    }
}
