package com.jwcode.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PreconditionsTest - Preconditions 工具类单元测试
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@DisplayName("Preconditions 工具类测试")
class PreconditionsTest {
    
    @Test
    @DisplayName("checkNotNull - 非 null 值应该返回原值")
    void checkNotNull_withNonNullValue_shouldReturnValue() {
        String value = "test";
        String result = Preconditions.checkNotNull(value, "value cannot be null");
        assertEquals("test", result);
    }
    
    @Test
    @DisplayName("checkNotNull - null 值应该抛出 NullPointerException")
    void checkNotNull_withNullValue_shouldThrowException() {
        assertThrows(NullPointerException.class, () -> {
            Preconditions.checkNotNull(null, "value cannot be null");
        });
    }
    
    @Test
    @DisplayName("checkArgument - true 条件不应该抛出异常")
    void checkArgument_withTrueCondition_shouldNotThrow() {
        assertDoesNotThrow(() -> {
            Preconditions.checkArgument(true, "condition failed");
        });
    }
    
    @Test
    @DisplayName("checkArgument - false 条件应该抛出 IllegalArgumentException")
    void checkArgument_withFalseCondition_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> {
            Preconditions.checkArgument(false, "condition failed");
        });
    }
}