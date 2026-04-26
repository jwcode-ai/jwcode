package com.jwcode.core.advanced.analyzer;

import com.jwcode.core.advanced.analyzer.CommandRecoveryAdvisor.RecoveryAdvice;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 智能项目分析器 - 对标 Kimi Code 的项目分析能力
 * 
 * 核心能力：
 * 1. 排噪取证：扫描时实时排除 .git / target / node_modules，按项目类型定位关键文件
 * 2. 假设驱动：读取配置后推导需要验证的代码文件，并自动验证存在性
 * 3. 错误恢复：记录并分析命令失败，给出下一步策略建议
 * 
 * 使用示例：
 * <pre>
 * SmartProjectAnalyzer analyzer = new SmartProjectAnalyzer("/path/to/project");
 * ProjectAnalysisReport report = analyzer.analyze();
 * System.out.println(report.toMarkdown());
 * </pre>
 */
public class SmartProjectAnalyzer {
    
    private final Path root;
    private final ProjectFingerprint fingerprint;
    private final EvidenceCollector evidenceCollector;
    private final HypothesisEngine hypothesisEngine;
    private final CommandRecoveryAdvisor recoveryAdvisor;
    private final List<String> recoveryNotes;
    
    private int totalFilesScanned = 0;
    private int noiseFilesSkipped = 0;
    
    public SmartProjectAnalyzer(String projectRoot) {
        this.root = Paths.get(projectRoot).toAbsolutePath().normalize();
        this.fingerprint = new ProjectFingerprint(this.root);
        this.evidenceCollector = new EvidenceCollector(this.root, this.fingerprint);
        this.hypothesisEngine = new HypothesisEngine(this.root, this.fingerprint);
        this.recoveryAdvisor = new CommandRecoveryAdvisor();
        this.recoveryNotes = new ArrayList<>();
    }
    
    /**
     * 执行完整智能分析
     */
    public ProjectAnalysisReport analyze() {
        long start = System.currentTimeMillis();
        
        // 阶段 1：快速目录扫描（计数 + 排噪验证）
        scanWithNoiseFilter();
        
        // 阶段 2：收集关键证据文件（按路径去重，保留最高优先级和合并来源）
        List<EvidenceCollector.EvidenceFile> rawEvidence = evidenceCollector.collect(30);
        Map<String, EvidenceCollector.EvidenceFile> dedupedEvidence = new LinkedHashMap<>();
        for (EvidenceCollector.EvidenceFile e : rawEvidence) {
            String rel = e.relativePath(root);
            EvidenceCollector.EvidenceFile existing = dedupedEvidence.get(rel);
            if (existing == null || e.priority() > existing.priority()) {
                dedupedEvidence.put(rel, e);
            }
        }
        List<EvidenceCollector.EvidenceFile> uniqueEvidence = new ArrayList<>(dedupedEvidence.values());
        
        List<ProjectAnalysisReport.EvidenceItem> evidenceItems = uniqueEvidence.stream()
            .map(e -> {
                String rel = e.relativePath(root);
                // 读取优先级 >= 60 的文件内容预览，让 Agent 看到更多真实内容
                String preview = e.priority() >= 60
                    ? evidenceCollector.readEvidenceContent(e.path(), 50)
                    : null;
                return new ProjectAnalysisReport.EvidenceItem(rel, e.priority(), e.source(), preview);
            })
            .collect(Collectors.toList());
        
        // 阶段 3：假设驱动推导
        List<HypothesisEngine.Hypothesis> rawHypotheses = hypothesisEngine.generateHypotheses(rawEvidence);
        List<ProjectAnalysisReport.HypothesisItem> hypothesisItems = rawHypotheses.stream()
            .map(h -> {
                List<String> verified = hypothesisEngine.verifyHypothesis(h).stream()
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .collect(Collectors.toList());
                return new ProjectAnalysisReport.HypothesisItem(h.description(), h.confidence(), verified);
            })
            .collect(Collectors.toList());
        
        long duration = System.currentTimeMillis() - start;
        
        return new ProjectAnalysisReport(
            root.toString(),
            fingerprint.getProjectType().getDisplayName(),
            fingerprint.getIndicators(),
            fingerprint.getMetadata(),
            evidenceItems,
            hypothesisItems,
            recoveryNotes,
            NoiseFilter.getFilterSummary(),
            duration,
            totalFilesScanned,
            noiseFilesSkipped
        );
    }
    
    /**
     * 分析外部命令失败，并记录恢复建议到报告
     * 
     * @param command 原始命令
     * @param stdout 命令标准输出
     * @param stderr 命令标准错误
     * @param exitCode 退出码
     * @return 恢复建议
     */
    public RecoveryAdvice adviseCommandRecovery(String command, String stdout, String stderr, int exitCode) {
        RecoveryAdvice advice = recoveryAdvisor.analyze(command, stdout, stderr, exitCode);
        
        StringBuilder note = new StringBuilder();
        note.append("[错误恢复] ").append(advice.diagnosis());
        note.append(" | 建议动作: ").append(advice.action());
        if (!advice.suggestions().isEmpty()) {
            note.append(" | 具体建议: ").append(String.join("; ", advice.suggestions()));
        }
        recoveryNotes.add(note.toString());
        
        return advice;
    }
    
    /**
     * 快速判断：当前失败是环境问题还是命令写法问题
     */
    public boolean isEnvironmentIssue(String command, String stdout, String stderr, int exitCode) {
        return recoveryAdvisor.isEnvironmentIssue(command, stdout, stderr, exitCode);
    }
    
    /**
     * 快速判断：当前失败是语法/写法问题
     */
    public boolean isSyntaxIssue(String command, String stdout, String stderr, int exitCode) {
        return recoveryAdvisor.isSyntaxIssue(command, stdout, stderr, exitCode);
    }
    
    /**
     * 获取高优先级文件列表（供外部 Tool 直接调用）
     */
    public List<String> getHighValueFiles(int maxCount) {
        return evidenceCollector.collect(maxCount).stream()
            .map(e -> root.relativize(e.path()).toString().replace('\\', '/'))
            .collect(Collectors.toList());
    }
    
    /**
     * 读取指定文件的预览内容
     */
    public String readFilePreview(String relativePath, int maxLines) {
        Path file = root.resolve(relativePath);
        if (!file.startsWith(root)) {
            return "[SECURITY] 路径越界";
        }
        return evidenceCollector.readEvidenceContent(file, maxLines);
    }
    
    /**
     * 根据配置关键字推导需要验证的文件
     */
    public List<String> deriveFilesFromConfig(String configKeyword) {
        List<HypothesisEngine.Hypothesis> hypotheses = hypothesisEngine.generateHypotheses(List.of());
        for (HypothesisEngine.Hypothesis h : hypotheses) {
            if (h.description().toLowerCase().contains(configKeyword.toLowerCase())) {
                return hypothesisEngine.verifyHypothesis(h).stream()
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .collect(Collectors.toList());
            }
        }
        return List.of();
    }
    
    private void scanWithNoiseFilter() {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (NoiseFilter.shouldSkipDirectory(dir)) {
                        noiseFilesSkipped++;
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    String rel = root.relativize(dir).toString();
                    int depth = rel.isEmpty() ? 0 : rel.split("[\\\\/]").length;
                    if (!NoiseFilter.isAcceptableDepth(depth)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    totalFilesScanned++;
                    if (NoiseFilter.isNoise(file)) {
                        noiseFilesSkipped++;
                    } else if (!NoiseFilter.isAcceptableSize(attrs.size())) {
                        noiseFilesSkipped++;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            recoveryNotes.add("[扫描警告] 目录扫描中断: " + e.getMessage());
        }
    }
    
    public Path getRoot() {
        return root;
    }
    
    public ProjectFingerprint getFingerprint() {
        return fingerprint;
    }
    
    /**
     * 获取项目中的所有源文件
     * 
     * @param projectRoot 项目根目录
     * @return 源文件路径列表
     */
    public List<Path> getSourceFiles(Path projectRoot) {
        List<Path> sourceFiles = new ArrayList<>();
        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (NoiseFilter.shouldSkipDirectory(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!NoiseFilter.isNoise(file) && NoiseFilter.isAcceptableSize(attrs.size())) {
                        String ext = file.getFileName().toString();
                        if (ext.endsWith(".java") || ext.endsWith(".ts") || ext.endsWith(".js") 
                            || ext.endsWith(".py") || ext.endsWith(".rs") || ext.endsWith(".go")) {
                            sourceFiles.add(file);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            recoveryNotes.add("[扫描警告] 源文件扫描失败: " + e.getMessage());
        }
        return sourceFiles;
    }
}
