package com.jwcode.core.checker;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 环境检测结果
 */
public class CheckerResult {

    private final LocalDateTime checkTime;
    private final List<DependencyInfo> dependencies;
    private final boolean overallSuccess;
    private final List<String> errors;
    private final List<String> warnings;

    private CheckerResult(List<DependencyInfo> dependencies, List<String> errors, List<String> warnings) {
        this.checkTime = LocalDateTime.now();
        this.dependencies = new ArrayList<>(dependencies);
        this.errors = new ArrayList<>(errors);
        this.warnings = new ArrayList<>(warnings);
        this.overallSuccess = dependencies.stream()
            .allMatch(d -> d.getStatus() == DependencyStatus.AVAILABLE || 
                          d.getStatus() == DependencyStatus.SKIPPED);
    }

    public static CheckerResult create() {
        return new CheckerResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public CheckerResult addDependency(DependencyInfo info) {
        this.dependencies.add(info);
        if (info.getStatus() == DependencyStatus.UNAVAILABLE) {
            this.errors.add(info.getName() + ": " + info.getMessage());
            if (info.getSuggestedFix() != null) {
                this.warnings.add(info.getName() + " 修复建议: " + info.getSuggestedFix());
            }
        }
        return this;
    }

    public CheckerResult addWarning(String warning) {
        this.warnings.add(warning);
        return this;
    }

    public CheckerResult build() {
        return new CheckerResult(this.dependencies, this.errors, this.warnings);
    }

    // Getters
    public LocalDateTime getCheckTime() { return checkTime; }
    public List<DependencyInfo> getDependencies() { return Collections.unmodifiableList(dependencies); }
    public boolean isOverallSuccess() { return overallSuccess; }
    public List<String> getErrors() { return Collections.unmodifiableList(errors); }
    public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }

    public List<DependencyInfo> getAvailableDependencies() {
        return dependencies.stream()
            .filter(DependencyInfo::isAvailable)
            .collect(Collectors.toList());
    }

    public List<DependencyInfo> getUnavailableDependencies() {
        return dependencies.stream()
            .filter(d -> d.getStatus() == DependencyStatus.UNAVAILABLE)
            .collect(Collectors.toList());
    }

    public List<DependencyInfo> getSkippedDependencies() {
        return dependencies.stream()
            .filter(d -> d.getStatus() == DependencyStatus.SKIPPED)
            .collect(Collectors.toList());
    }

    public int getTotalCount() { return dependencies.size(); }
    public int getAvailableCount() { return (int) dependencies.stream().filter(DependencyInfo::isAvailable).count(); }
    public int getUnavailableCount() { return (int) dependencies.stream().filter(d -> d.getStatus() == DependencyStatus.UNAVAILABLE).count(); }
    public int getSkippedCount() { return (int) dependencies.stream().filter(d -> d.getStatus() == DependencyStatus.SKIPPED).count(); }

    public String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ 环境检测结果 ═══\n");
        sb.append(String.format("检测时间: %s\n", checkTime));
        sb.append(String.format("总计依赖: %d\n", getTotalCount()));
        sb.append(String.format("可用: %d | 不可用: %d | 跳过: %d\n", 
            getAvailableCount(), getUnavailableCount(), getSkippedCount()));
        
        if (!errors.isEmpty()) {
            sb.append("\n⚠️ 错误:\n");
            errors.forEach(e -> sb.append("  - ").append(e).append("\n"));
        }
        
        if (!warnings.isEmpty()) {
            sb.append("\n💡 修复建议:\n");
            warnings.forEach(w -> sb.append("  - ").append(w).append("\n"));
        }
        
        return sb.toString();
    }
}
