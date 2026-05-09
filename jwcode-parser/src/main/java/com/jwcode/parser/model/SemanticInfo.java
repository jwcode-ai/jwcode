package com.jwcode.parser.model;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 语义分析结果。
 *
 * <p>包含从源码中提取的类型引用、方法调用、导入统计等语义信息。
 * 与 ParseResult 配合使用，提供更丰富的代码理解能力。</p>
 */
@Data
public class SemanticInfo {

    /** 引用的类型名称列表 */
    private List<String> typeReferences;

    /** 方法调用列表 */
    private List<MethodCall> methodCalls;

    /** 导入语句数量 */
    private int importCount;

    /** 符号数量 */
    private int symbolCount;

    public SemanticInfo() {
        this.typeReferences = Collections.emptyList();
        this.methodCalls = Collections.emptyList();
    }

    /**
     * 方法调用信息
     */
    @Data
    public static class MethodCall {
        /** 调用目标（如变量名、类名） */
        private final String target;

        /** 方法名 */
        private final String methodName;

        public MethodCall(String target, String methodName) {
            this.target = target;
            this.methodName = methodName;
        }
    }

    /**
     * 获取所有引用的外部类型（排除 java.lang 中的类型）
     */
    public List<String> getExternalTypeReferences() {
        if (typeReferences == null) return Collections.emptyList();
        return typeReferences.stream()
                .filter(t -> !isJavaLangType(t))
                .toList();
    }

    private boolean isJavaLangType(String name) {
        return switch (name) {
            case "String", "Integer", "Long", "Double", "Float", "Boolean",
                 "Object", "Class", "System", "Thread", "Exception",
                 "RuntimeException", "Throwable", "Math", "Number" -> true;
            default -> false;
        };
    }
}
