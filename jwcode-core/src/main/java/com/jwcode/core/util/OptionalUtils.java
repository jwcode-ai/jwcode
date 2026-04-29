package com.jwcode.core.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Optional 工具类
 * 
 * 提供便捷的 Optional 操作方法，减少 NPE。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public final class OptionalUtils {
    
    private OptionalUtils() {
        // 工具类，禁止实例化
    }
    
    // ============ 基本操作 ============
    
    /**
     * 如果值为 null，返回空 Optional
     */
    public static <T> Optional<T> of(T value) {
        return Optional.ofNullable(value);
    }
    
    /**
     * 如果值为 null 或空字符串，返回空 Optional
     */
    public static Optional<String> ofBlank(String value) {
        return Optional.ofNullable(value)
            .filter(s -> !s.trim().isEmpty());
    }
    
    /**
     * 如果集合为 null 或空，返回空 Optional
     */
    public static <T extends Collection<?>> Optional<T> ofEmpty(T collection) {
        return Optional.ofNullable(collection)
            .filter(c -> !c.isEmpty());
    }
    
    /**
     * 如果 Map 为 null 或空，返回空 Optional
     */
    public static <T extends Map<?, ?>> Optional<T> ofEmpty(T map) {
        return Optional.ofNullable(map)
            .filter(m -> !m.isEmpty());
    }
    
    // ============ 安全转换 ============
    
    /**
     * 安全调用方法，如果对象为 null 返回默认值
     */
    public static <T, R> Optional<R> map(T object, Function<T, R> mapper) {
        return Optional.ofNullable(object).map(mapper);
    }
    
    /**
     * 安全调用方法，如果对象为 null 返回默认值
     */
    public static <T, R> R mapOrDefault(T object, Function<T, R> mapper, R defaultValue) {
        return Optional.ofNullable(object)
            .map(mapper)
            .orElse(defaultValue);
    }
    
    /**
     * 安全调用方法，如果对象为 null 返回默认值
     */
    public static <T, R> R mapOrNull(T object, Function<T, R> mapper) {
        return Optional.ofNullable(object)
            .map(mapper)
            .orElse(null);
    }
    
    // ============ 条件执行 ============
    
    /**
     * 如果值存在则执行操作
     */
    public static <T> void ifPresent(T value, Consumer<T> action) {
        Optional.ofNullable(value).ifPresent(action);
    }
    
    /**
     * 如果值存在则执行操作，否则执行默认操作
     */
    public static <T> void ifPresentOrElse(T value, Consumer<T> action, Runnable emptyAction) {
        Optional.ofNullable(value).ifPresentOrElse(action, emptyAction);
    }
    
    // ============ 空集合安全 ============
    
    /**
     * 返回空集合而非 null
     */
    public static <T> Collection<T> emptyIfNull(Collection<T> collection) {
        return collection != null ? collection : Collections.emptyList();
    }
    
    /**
     * 返回空 Map 而非 null
     */
    public static <K, V> Map<K, V> emptyIfNull(Map<K, V> map) {
        return map != null ? map : Collections.emptyMap();
    }
    
    /**
     * 返回空数组而非 null
     */
    @SafeVarargs
    public static <T> T[] emptyIfNull(T... array) {
        return array != null ? array : (T[]) new Object[0];
    }
    
    // ============ 字符串安全 ============
    
    /**
     * 安全获取字符串，如果为 null 返回空字符串
     */
    public static String emptyIfNull(String str) {
        return str != null ? str : "";
    }
    
    /**
     * 安全获取字符串，如果为 null 返回默认值
     */
    public static String defaultIfNull(String str, String defaultValue) {
        return str != null ? str : defaultValue;
    }
    
    // ============ 链式操作 ============
    
    /**
     * 尝试多个 Optional，返回第一个有值的
     */
    @SafeVarargs
    public static <T> Optional<T> firstPresent(Optional<T>... optionals) {
        for (Optional<T> opt : optionals) {
            if (opt != null && opt.isPresent()) {
                return opt;
            }
        }
        return Optional.empty();
    }
    
    /**
     * 尝试多个 Optional Supplier，返回第一个有值的
     */
    @SafeVarargs
    public static <T> Optional<T> firstPresent(Supplier<Optional<T>>... suppliers) {
        for (Supplier<Optional<T>> supplier : suppliers) {
            if (supplier != null) {
                Optional<T> result = supplier.get();
                if (result != null && result.isPresent()) {
                    return result;
                }
            }
        }
        return Optional.empty();
    }
    
    // ============ 过滤操作 ============
    
    /**
     * 过滤非空值
     */
    public static <T> Optional<T> filterNonNull(T value) {
        return Optional.ofNullable(value);
    }
    
    /**
     * 过滤非空且非空字符串
     */
    public static Optional<String> filterNonBlank(String value) {
        return Optional.ofNullable(value)
            .filter(s -> !s.trim().isEmpty());
    }
    
    // ============ 断言 ============
    
    /**
     * 检查值是否非空
     */
    public static <T> boolean isPresent(T value) {
        return value != null;
    }
    
    /**
     * 检查字符串是否非空
     */
    public static boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }
    
    /**
     * 检查集合是否非空
     */
    public static <T extends Collection<?>> boolean isNotEmpty(T collection) {
        return collection != null && !collection.isEmpty();
    }
    
    /**
     * 检查 Map 是否非空
     */
    public static <T extends Map<?, ?>> boolean isNotEmpty(T map) {
        return map != null && !map.isEmpty();
    }
    
    // ============ 复杂对象安全访问 ============
    
    /**
     * 安全获取嵌套属性
     */
    public static <T, R> Optional<R> nestedGet(T root, Function<T, R> getter) {
        return Optional.ofNullable(root).map(getter);
    }
    
    /**
     * 安全获取嵌套 Map 中的值
     */
    public static <K, V> Optional<V> mapGet(Map<K, V> map, K key) {
        return Optional.ofNullable(map)
            .flatMap(m -> Optional.ofNullable(m.get(key)));
    }
    
    /**
     * 安全获取嵌套数组中的值
     */
    public static <T> Optional<T> arrayGet(T[] array, int index) {
        if (array == null || index < 0 || index >= array.length) {
            return Optional.empty();
        }
        return Optional.ofNullable(array[index]);
    }
    
    // ============ 流转换 ============
    
    /**
     * 将可能为 null 的集合转换为流
     */
    public static <T> java.util.stream.Stream<T> stream(Collection<T> collection) {
        return collection != null ? collection.stream() : java.util.stream.Stream.empty();
    }
    
    /**
     * 将可能为 null 的数组转换为流
     */
    @SafeVarargs
    public static <T> java.util.stream.Stream<T> stream(T... array) {
        return array != null ? java.util.Arrays.stream(array) : java.util.stream.Stream.empty();
    }
    
    // ============ 转换操作 ============
    
    /**
     * 将 null 转换为空 Optional
     */
    public static <T> Optional<T> toOptional(T value) {
        return Optional.ofNullable(value);
    }
    
    /**
     * 将 Optional 转换为可能为 null 的值
     */
    public static <T> T orNull(Optional<T> optional) {
        return optional != null ? optional.orElse(null) : null;
    }
    
    /**
     * 将 Optional 转换为默认值
     */
    public static <T> T orDefault(Optional<T> optional, T defaultValue) {
        return optional != null ? optional.orElse(defaultValue) : defaultValue;
    }
}