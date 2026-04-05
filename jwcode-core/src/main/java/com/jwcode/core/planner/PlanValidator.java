package com.jwcode.core.planner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 计划验证器
 */
public class PlanValidator {
    
    /**
     * 验证计划步骤
     */
    public List<String> validate(List<PlanStep> steps) {
        List<String> issues = new ArrayList<>();
        
        // 检查空步骤
        if (steps == null || steps.isEmpty()) {
            issues.add("计划为空，没有可执行的步骤");
            return issues;
        }
        
        // 检查循环依赖
        if (hasCircularDependency(steps)) {
            issues.add("检测到步骤间存在循环依赖");
        }
        
        // 检查不存在的依赖
        Set<String> validStepNumbers = new HashSet<>();
        for (PlanStep step : steps) {
            validStepNumbers.add(String.valueOf(step.getStepNumber()));
        }
        
        for (PlanStep step : steps) {
            for (String dep : step.getDependencies()) {
                if (!validStepNumbers.contains(dep)) {
                    issues.add("步骤 " + step.getStepNumber() + " 依赖不存在的步骤: " + dep);
                }
            }
        }
        
        // 检查步骤编号连续性
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getStepNumber() != i + 1) {
                issues.add("步骤编号不连续，建议按顺序编号以便追踪");
                break;
            }
        }
        
        // 检查描述完整性
        for (PlanStep step : steps) {
            if (step.getDescription() == null || step.getDescription().trim().isEmpty()) {
                issues.add("步骤 " + step.getStepNumber() + " 缺少描述");
            }
        }
        
        // 检查过于复杂的依赖链
        int maxDependencyDepth = calculateMaxDependencyDepth(steps);
        if (maxDependencyDepth > steps.size() / 2) {
            issues.add("依赖链过长，可能影响并行执行效率");
        }
        
        return issues;
    }
    
    /**
     * 检测循环依赖
     */
    private boolean hasCircularDependency(List<PlanStep> steps) {
        for (PlanStep step : steps) {
            Set<String> visited = new HashSet<>();
            if (hasCycle(step, steps, visited, new HashSet<>())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean hasCycle(PlanStep step, List<PlanStep> allSteps, Set<String> visited, Set<String> recursionStack) {
        String stepId = String.valueOf(step.getStepNumber());
        
        if (recursionStack.contains(stepId)) {
            return true;
        }
        
        if (visited.contains(stepId)) {
            return false;
        }
        
        visited.add(stepId);
        recursionStack.add(stepId);
        
        for (String depId : step.getDependencies()) {
            PlanStep depStep = findStepByNumber(allSteps, depId);
            if (depStep != null && hasCycle(depStep, allSteps, visited, recursionStack)) {
                return true;
            }
        }
        
        recursionStack.remove(stepId);
        return false;
    }
    
    private PlanStep findStepByNumber(List<PlanStep> steps, String number) {
        return steps.stream()
            .filter(s -> String.valueOf(s.getStepNumber()).equals(number))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 计算最大依赖深度
     */
    private int calculateMaxDependencyDepth(List<PlanStep> steps) {
        int maxDepth = 0;
        for (PlanStep step : steps) {
            int depth = calculateDepth(step, steps, new HashSet<>());
            maxDepth = Math.max(maxDepth, depth);
        }
        return maxDepth;
    }
    
    private int calculateDepth(PlanStep step, List<PlanStep> allSteps, Set<String> visited) {
        String stepId = String.valueOf(step.getStepNumber());
        if (visited.contains(stepId) || step.getDependencies().isEmpty()) {
            return 0;
        }
        
        visited.add(stepId);
        int maxDepDepth = 0;
        
        for (String depId : step.getDependencies()) {
            PlanStep depStep = findStepByNumber(allSteps, depId);
            if (depStep != null) {
                int depDepth = calculateDepth(depStep, allSteps, new HashSet<>(visited));
                maxDepDepth = Math.max(maxDepDepth, depDepth);
            }
        }
        
        return maxDepDepth + 1;
    }
}
