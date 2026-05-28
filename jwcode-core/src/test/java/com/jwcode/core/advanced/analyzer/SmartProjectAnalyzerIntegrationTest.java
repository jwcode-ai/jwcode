package com.jwcode.core.advanced.analyzer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * 集成测试：对当前项目运行 SmartAnalyzeTool。
 * 需要真实项目目录，CI 环境默认禁用。
 */
class SmartProjectAnalyzerIntegrationTest {

    @Test
    @Disabled("Requires a real project directory; set project.root system property to enable")
    void printAgentOptimizedReport() {
        String projectRoot = System.getProperty("project.root", System.getProperty("user.dir"));
        SmartProjectAnalyzer analyzer = new SmartProjectAnalyzer(projectRoot);
        ProjectAnalysisReport report = analyzer.analyze();

        System.out.println("========== AGENT OPTIMIZED REPORT ==========");
        System.out.println(report.toAgentOptimized());
        System.out.println("========== END REPORT ==========");
    }
}
