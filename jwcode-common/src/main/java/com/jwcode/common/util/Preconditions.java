package com.jwcode.common.util;

import java.util.Objects;

/**
 * Preconditions - 参数校验工具类
 * 
 * 功能说明：
 * 提供类似于 Guava Preconditions 的参数校验功能，
 * 用于在方法开始时验证参数合法性，提前抛出异常。
 * 
 * 上下文关系：
 * - 被各个模块的工具类和服务类使用
 * - 提供统一的参数校验方式
 * 
 * 使用示例：
 * <pre>{@code
 * public void process(String input) {
 *     Preconditions.checkNotNull(input, "input cannot be null");
 *     Preconditions.checkArgument(!input.isEmpty(), "input cannot be empty");
 * }
 * }</pre>
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public final class Preconditions {

    private Preconditions() {
        // 私有构造函数，防止实例化
    }

    /**
     * 检查对象是否为 null
     * 
     * @param obj 待检查的对象
     * @param message 错误消息
     * @param <T> 对象类型
     * @return 非 null 的对象
     * @throws NullPointerException 如果对象为 null
     */
    public static <T> T checkNotNull(T obj, String message) {
        if (obj == null) {
            throw new NullPointerException(message);
        }
        return obj;
    }

    /**
     * 检查对象是否为 null，支持格式化消息
     * 
     * @param obj 待检查的对象
     * @param message 错误消息模板
     * @param args 消息参数
     * @param <T> 对象类型
     * @return 非 null 的对象
     * @throws NullPointerException 如果对象为 null
     */
    public static <T> T checkNotNull(T obj, String message, Object... args) {
        if (obj == null) {
            throw new NullPointerException(format(message, args));
        }
        return obj;
    }

    /**
     * 检查条件是否为 true
     * 
     * @param condition 条件
     * @param message 错误消息
     * @throws IllegalArgumentException 如果条件为 false
     */
    public static void checkArgument(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 检查条件是否为 true，支持格式化消息
     * 
     * @param condition 条件
     * @param message 错误消息模板
     * @param args 消息参数
     * @throws IllegalArgumentException 如果条件为 false
     */
    public static void checkArgument(boolean condition, String message, Object... args) {
        if (!condition) {
            throw new IllegalArgumentException(format(message, args));
        }
    }

    /**
     * 检查状态是否为 true
     * 
     * @param condition 条件
     * @param message 错误消息
     * @throws IllegalStateException 如果条件为 false
     */
    public static void checkState(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * 检查状态是否为 true，支持格式化消息
     * 
     * @param condition 条件
     * @param message 错误消息模板
     * @param args 消息参数
     * @throws IllegalStateException 如果条件为 false
     */
    public static void checkState(boolean condition, String message, Object... args) {
        if (!condition) {
            throw new IllegalStateException(format(message, args));
        }
    }

    /**
     * 检查索引是否在范围内 [0, size)
     * 
     * @param index 索引值
     * @param size 范围大小
     * @return 索引值
     * @throws IndexOutOfBoundsException 如果索引越界
     */
    public static int checkElementIndex(int index, int size) {
        return checkElementIndex(index, size, "index");
    }

    /**
     * 检查索引是否在范围内 [0, size)，支持自定义消息
     * 
     * @param index 索引值
     * @param size 范围大小
     * @param desc 描述
     * @return 索引值
     * @throws IndexOutOfBoundsException 如果索引越界
     */
    public static int checkElementIndex(int index, int size, String desc) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(badElementIndex(index, size, desc));
        }
        return index;
    }

    /**
     * 检查位置是否在范围内 [0, size]
     * 
     * @param index 位置值
     * @param size 范围大小
     * @return 位置值
     * @throws IndexOutOfBoundsException 如果位置越界
     */
    public static int checkPositionIndex(int index, int size) {
        return checkPositionIndex(index, size, "index");
    }

    /**
     * 检查位置是否在范围内 [0, size]，支持自定义消息
     * 
     * @param index 位置值
     * @param size 范围大小
     * @param desc 描述
     * @return 位置值
     * @throws IndexOutOfBoundsException 如果位置越界
     */
    public static int checkPositionIndex(int index, int size, String desc) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException(badPositionIndex(index, size, desc));
        }
        return index;
    }

    private static String badElementIndex(int index, int size, String desc) {
        if (index < 0) {
            return String.format("%s (%s) must not be negative", desc, index);
        } else if (size < 0) {
            throw new IllegalArgumentException("negative size: " + size);
        } else {
            return String.format("%s (%s) must be less than size (%s)", desc, index, size);
        }
    }

    private static String badPositionIndex(int index, int size, String desc) {
        if (index < 0) {
            return String.format("%s (%s) must not be negative", desc, index);
        } else if (size < 0) {
            throw new IllegalArgumentException("negative size: " + size);
        } else {
            return String.format("%s (%s) must not be greater than size (%s)", desc, index, size);
        }
    }

    private static String format(String template, Object... args) {
        // 简单的字符串格式化
        if (args == null || args.length == 0) {
            return template;
        }
        
        StringBuilder sb = new StringBuilder(template.length() + 16 * args.length);
        int templateStart = 0;
        int i = 0;
        
        while (i < args.length) {
            int placeholderStart = template.indexOf("%s", templateStart);
            if (placeholderStart == -1) {
                break;
            }
            sb.append(template, templateStart, placeholderStart);
            sb.append(args[i++]);
            templateStart = placeholderStart + 2;
        }
        
        sb.append(template, templateStart, template.length());
        
        while (i < args.length) {
            sb.append(" [");
            sb.append(args[i++]);
            sb.append("]");
        }
        
        return sb.toString();
    }

    /**
     * 检查字符串是否非空
     * 
     * @param str 待检查的字符串
     * @param message 错误消息
     * @return 非空字符串
     * @throws IllegalArgumentException 如果字符串为空
     */
    public static String checkNotEmpty(String str, String message) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }

    /**
     * 检查字符串是否非空，支持格式化消息
     * 
     * @param str 待检查的字符串
     * @param message 错误消息模板
     * @param args 消息参数
     * @return 非空字符串
     * @throws IllegalArgumentException 如果字符串为空
     */
    public static String checkNotEmpty(String str, String message, Object... args) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException(format(message, args));
        }
        return str;
    }
}