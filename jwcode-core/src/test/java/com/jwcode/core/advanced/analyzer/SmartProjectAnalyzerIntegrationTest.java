package com.jwcode.core.advanced.analyzer;

import org.junit.jupiter.api.Test;

/**
 * 集成测试：对真实项目 jwclaw 运行 SmartAnalyzeTool，观察 agent 格式输出
 */
class SmartProjectAnalyzerIntegrationTest {

    @Test
    void printAgentOptimizedReportForJwclaw() {
        String projectRoot = "C:/Users/HUAWEI/Desktop/jwclaw/jwclaw";
        SmartProjectAnalyzer analyzer = new SmartProjectAnalyzer(projectRoot);
        ProjectAnalysisReport report = analyzer.analyze();

        System.out.println("========== AGENT OPTIMIZED REPORT ==========");
        System.out.println(report.toAgentOptimized());
        System.out.println("========== END REPORT ==========");
    }
}
