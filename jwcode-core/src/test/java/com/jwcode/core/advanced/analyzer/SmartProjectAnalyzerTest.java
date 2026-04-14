package com.jwcode.core.advanced.analyzer;

import com.jwcode.core.advanced.analyzer.CommandRecoveryAdvisor.RecoveryAdvice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmartProjectAnalyzer 单元测试
 */
class SmartProjectAnalyzerTest {

    @TempDir
    Path tempDir;

    @Test
    void testNoiseFilterExcludesGitAndNodeModules() throws Exception {
        // 创建模拟项目结构
        Files.createDirectories(tempDir.resolve(".git/objects/aa"));
        Files.createDirectories(tempDir.resolve("node_modules/lodash"));
        Files.createDirectories(tempDir.resolve("target/classes"));
        Files.createDirectories(tempDir.resolve("src/main/java/com/example"));
        
        Files.writeString(tempDir.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");
        Files.writeString(tempDir.resolve("src/main/java/com/example/App.java"), "public class App {}");
        Files.writeString(tempDir.resolve(".git/config"), "[core]");
        Files.writeString(tempDir.resolve("node_modules/lodash/index.js"), "module.exports = {};");
        
        SmartProjectAnalyzer analyzer = new SmartProjectAnalyzer(tempDir.toString());
        ProjectAnalysisReport report = analyzer.analyze();
        
        assertTrue(report.getTotalFilesScanned() > 0);
        // 噪音文件应该被跳过
        assertTrue(report.getNoiseFilesSkipped() >= 3);
        
        // 证据文件中不应包含噪音目录的内容
        List<String> evidencePaths = report.getEvidence().stream()
            .map(ProjectAnalysisReport.EvidenceItem::relativePath)
            .toList();
        
        for (String p : evidencePaths) {
            assertFalse(p.contains(".git/"), "不应包含 .git: " + p);
            assertFalse(p.contains("node_modules/"), "不应包含 node_modules: " + p);
            assertFalse(p.contains("target/"), "不应包含 target: " + p);
        }
        
        // 应该识别到 Maven 项目
        assertEquals("Maven", report.getProjectType());
    }

    @Test
    void testSpringBootHypothesisGeneration() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), """
            <project>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                </parent>
            </project>
            """);
        
        Files.createDirectories(tempDir.resolve("src/main/java/com/demo"));
        Files.writeString(tempDir.resolve("src/main/java/com/demo/App.java"), 
            "package com.demo; @SpringBootApplication public class App {}");
        Files.createDirectories(tempDir.resolve("src/main/resources"));
        Files.writeString(tempDir.resolve("src/main/resources/application.yml"), """
            jwclaw:
              distributed:
                enabled: true
            spring:
              datasource:
                url: jdbc:mysql://localhost/test
            """);
        
        SmartProjectAnalyzer analyzer = new SmartProjectAnalyzer(tempDir.toString());
        ProjectAnalysisReport report = analyzer.analyze();
        
        // 应该识别为 Spring Boot
        assertEquals("Maven + Spring Boot", report.getProjectType());
        
        // 应该生成分布式和数据库相关的假设
        boolean hasDistributedHypothesis = report.getHypotheses().stream()
            .anyMatch(h -> h.description().contains("分布式"));
        boolean hasDbHypothesis = report.getHypotheses().stream()
            .anyMatch(h -> h.description().contains("数据库"));
        
        assertTrue(hasDistributedHypothesis, "应推导出分布式假设");
        assertTrue(hasDbHypothesis, "应推导出数据库假设");
    }

    @Test
    void testCommandRecoveryRegexEscape() {
        SmartProjectAnalyzer analyzer = new SmartProjectAnalyzer(tempDir.toString());
        RecoveryAdvice advice = analyzer.adviseCommandRecovery(
            "Get-ChildItem | Where-Object { $_.FullName -notmatch '\\.git\\' }",
            "",
            "ArgumentException: 正在分析 \\.git\\ 的 \\ 模式末尾是否合法",
            1
        );
        
        assertEquals(CommandRecoveryAdvisor.RecoveryAction.FIX_SYNTAX, advice.action());
        assertTrue(advice.diagnosis().contains("正则") || advice.diagnosis().contains("转义"));
        assertTrue(advice.confidence() > 90);
    }

    @Test
    void testCommandRecoveryCommandNotFound() {
        SmartProjectAnalyzer analyzer = new SmartProjectAnalyzer(tempDir.toString());
        RecoveryAdvice advice = analyzer.adviseCommandRecovery(
            "head -20 file.txt",
            "",
            "'head' is not recognized as an internal or external command",
            1
        );
        
        assertEquals(CommandRecoveryAdvisor.RecoveryAction.SWITCH_TOOL, advice.action());
        assertFalse(advice.suggestions().isEmpty());
    }

    @Test
    void testCommandRecoveryTruncatedOutput() {
        SmartProjectAnalyzer analyzer = new SmartProjectAnalyzer(tempDir.toString());
        RecoveryAdvice advice = analyzer.adviseCommandRecovery(
            "find . -type f",
            "Output is truncated to fit in the message",
            "",
            0
        );
        
        assertEquals(CommandRecoveryAdvisor.RecoveryAction.NARROW_SCOPE, advice.action());
        assertTrue(advice.diagnosis().contains("截断") || advice.diagnosis().contains("truncated"));
    }

    @Test
    void testIsEnvironmentIssueVsSyntaxIssue() {
        SmartProjectAnalyzer analyzer = new SmartProjectAnalyzer(tempDir.toString());
        
        assertTrue(analyzer.isEnvironmentIssue(
            "cd /nonexistent", "", "No such file or directory", 1));
        
        assertTrue(analyzer.isSyntaxIssue(
            "grep -P '(?<!\\\\)'", "", "unrecognized escape", 2));
        
        assertFalse(analyzer.isEnvironmentIssue(
            "grep -P '(?<!\\\\)'", "", "unrecognized escape", 2));
    }

    @Test
    void testGetHighValueFiles() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
        Files.createDirectories(tempDir.resolve("src/main/java/com/app"));
        Files.writeString(tempDir.resolve("src/main/java/com/app/Application.java"), "public class Application {}");
        
        SmartProjectAnalyzer analyzer = new SmartProjectAnalyzer(tempDir.toString());
        List<String> files = analyzer.getHighValueFiles(10);
        
        assertFalse(files.isEmpty());
        assertTrue(files.stream().anyMatch(f -> f.contains("pom.xml")));
        assertTrue(files.stream().anyMatch(f -> f.contains("Application.java")));
    }
}
