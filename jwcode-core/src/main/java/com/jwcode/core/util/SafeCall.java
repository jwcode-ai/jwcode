package com.jwcode.core.util;

/**
 * 安全的函数调用接口
 * 
 * 用于包装可能抛出异常的方法调用。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public final class SafeCall {
    
    private SafeCall() {
        // 工具类，禁止实例化
    }
    
    /**
     * 安全调用方法，捕获异常并返回 null
     */
    public static <T> T call(SafeSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 安全调用方法，捕获异常并返回默认值
     */
    public static <T> T call(SafeSupplier<T> supplier, T defaultValue) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * 安全调用方法，捕获异常并返回 Optional.empty()
     */
    public static <T> java.util.Optional<T> callOptional(SafeSupplier<T> supplier) {
        try {
            return java.util.Optional.ofNullable(supplier.get());
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }
    
    /**
     * 安全执行 Runnable，捕获异常
     */
    public static void run(SafeRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            // 忽略异常
        }
    }
    
    /**
     * 安全执行 Runnable，捕获异常并记录
     */
    public static void runAndLog(SafeRunnable runnable, java.util.function.Consumer<Exception> errorHandler) {
        try {
            runnable.run();
        } catch (Exception e) {
            if (errorHandler != null) {
                errorHandler.accept(e);
            }
        }
    }
    
    /**
     * 安全调用方法，异常时抛出运行时异常
     */
    public static <T> T callOrThrow(SafeSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 安全调用方法，异常时抛出指定异常
     */
    public static <T, E extends Throwable> T callOrThrow(SafeSupplier<T> supplier, java.util.function.Function<Exception, E> exceptionMapper) throws E {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw exceptionMapper.apply(e);
        } catch (Exception e) {
            throw exceptionMapper.apply(e);
        }
    }
    
    /**
     * 安全的Supplier接口
     */
    @FunctionalInterface
    public interface SafeSupplier<T> {
        T get() throws Exception;
    }
    
    /**
     * 安全的Runnable接口
     */
    @FunctionalInterface
    public interface SafeRunnable {
        void run() throws Exception;
    }
}