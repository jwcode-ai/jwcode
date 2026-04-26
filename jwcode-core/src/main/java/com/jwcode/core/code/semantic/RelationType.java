package com.jwcode.core.code.semantic;

/**
 * 关系类型
 */
public enum RelationType {
    DEFINES,      // 定义
    REFERENCES,   // 引用
    CALLS,        // 调用
    INHERITS,     // 继承
    IMPLEMENTS,   // 实现
    IMPORTS,      // 导入
    CONTAINS,     // 包含
    USES,         // 使用
    DEPENDS_ON,   // 依赖
    OVERRIDES,    // 重写
    ALIASES       // 别名
}
