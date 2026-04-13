package com.jwcode.core.repl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * REPL 执行器工厂
 * 根据语言返回对应的 REPLExecutor 实例，支持缓存
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class REPLFactory {
    
    private static final Logger logger = Logger.getLogger(REPLFactory.class.getName());
    
    // 缓存的 REPL 实例
    private final Map<String, REPLExecutor> executorCache;
    private final Object cacheLock = new Object();
    
    // 默认配置
    private final long defaultTimeoutMillis;
    private final long defaultMaxMemoryMB;
    
    // 单例实例
    private static volatile REPLFactory instance;
    private static final Object instanceLock = new Object();
    
    /**
     * 获取单例实例
     */
    public static REPLFactory getInstance() {
        if (instance == null) {
            synchronized (instanceLock) {
                if (instance == null) {
                    instance = new REPLFactory();
                }
            }
        }
        return instance;
    }
    
    /**
     * 创建新的工厂实例（用于自定义配置）
     */
    public static REPLFactory create(long timeoutMillis, long maxMemoryMB) {
        return new REPLFactory(timeoutMillis, maxMemoryMB);
    }
    
    /**
     * 默认构造函数
     * 超时 30 秒，内存限制 256MB
     */
    public REPLFactory() {
        this(30000, 256);
    }
    
    /**
     * 创建 REPL 工厂
     * 
     * @param defaultTimeoutMillis 默认超时时间（毫秒）
     * @param defaultMaxMemoryMB 默认最大内存限制（MB）
     */
    public REPLFactory(long defaultTimeoutMillis, long defaultMaxMemoryMB) {
        this.executorCache = new ConcurrentHashMap<>();
        this.defaultTimeoutMillis = defaultTimeoutMillis;
        this.defaultMaxMemoryMB = defaultMaxMemoryMB;
    }
    
    /**
     * 获取 REPL 执行器（使用默认配置）
     * 
     * @param language 编程语言
     * @return REPL 执行器实例
     */
    public REPLExecutor getExecutor(String language) {
        return getExecutor(language, defaultTimeoutMillis, defaultMaxMemoryMB);
    }
    
    /**
     * 获取 REPL 执行器
     * 
     * @param language 编程语言
     * @param timeoutMillis 超时时间（毫秒）
     * @param maxMemoryMB 最大内存限制（MB）
     * @return REPL 执行器实例
     */
    public REPLExecutor getExecutor(String language, long timeoutMillis, long maxMemoryMB) {
        if (language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("Language cannot be null or empty");
        }
        
        String normalizedLang = normalizeLanguage(language);
        String cacheKey = buildCacheKey(normalizedLang, timeoutMillis, maxMemoryMB);
        
        // 先从缓存中查找
        REPLExecutor cached = executorCache.get(cacheKey);
        if (cached != null && cached.isAvailable()) {
            logger.fine("Using cached REPL executor for: " + normalizedLang);
            return cached;
        }
        
        // 创建新的执行器
        synchronized (cacheLock) {
            // 双重检查
            cached = executorCache.get(cacheKey);
            if (cached != null && cached.isAvailable()) {
                return cached;
            }
            
            // 创建新的执行器
            REPLExecutor executor = createExecutor(normalizedLang, timeoutMillis, maxMemoryMB);
            if (executor != null) {
                executorCache.put(cacheKey, executor);
                logger.info("Created new REPL executor for: " + normalizedLang);
            }
            return executor;
        }
    }
    
    /**
     * 创建 REPL 执行器实例
     */
    private REPLExecutor createExecutor(String language, long timeoutMillis, long maxMemoryMB) {
        return switch (language) {
            case "java" -> new JavaREPLExecutor(timeoutMillis, maxMemoryMB);
            case "python" -> new PythonREPLExecutor("python3", timeoutMillis, maxMemoryMB);
            case "python2" -> new PythonREPLExecutor("python2", timeoutMillis, maxMemoryMB);
            case "javascript", "js" -> new JavaScriptREPLExecutor(timeoutMillis, maxMemoryMB);
            default -> {
                logger.warning("Unsupported language: " + language);
                yield null;
            }
        };
    }
    
    /**
     * 规范化语言名称
     */
    private String normalizeLanguage(String language) {
        String lower = language.toLowerCase().trim();
        return switch (lower) {
            case "js", "javascript", "node", "nodejs" -> "javascript";
            case "py", "python3" -> "python";
            case "python2" -> "python2";
            default -> lower;
        };
    }
    
    /**
     * 构建缓存键
     */
    private String buildCacheKey(String language, long timeoutMillis, long maxMemoryMB) {
        return language + "_" + timeoutMillis + "_" + maxMemoryMB;
    }
    
    /**
     * 检查是否支持某种语言
     * 
     * @param language 编程语言
     * @return 如果支持返回 true
     */
    public boolean isSupported(String language) {
        String normalized = normalizeLanguage(language);
        return switch (normalized) {
            case "java", "python", "python2", "javascript" -> true;
            default -> false;
        };
    }
    
    /**
     * 获取支持的语言列表
     * 
     * @return 支持的语言数组
     */
    public String[] getSupportedLanguages() {
        return new String[]{"java", "python", "javascript"};
    }
    
    /**
     * 清除所有缓存的执行器
     */
    public void clearCache() {
        synchronized (cacheLock) {
            for (REPLExecutor executor : executorCache.values()) {
                try {
                    executor.shutdown();
                } catch (Exception e) {
                    logger.fine("Error shutting down executor: " + e.getMessage());
                }
            }
            executorCache.clear();
            logger.info("REPL executor cache cleared");
        }
    }
    
    /**
     * 移除特定语言的缓存
     * 
     * @param language 编程语言
     */
    public void removeFromCache(String language) {
        String normalizedLang = normalizeLanguage(language);
        synchronized (cacheLock) {
            executorCache.entrySet().removeIf(entry -> {
                if (entry.getKey().startsWith(normalizedLang + "_")) {
                    try {
                        entry.getValue().shutdown();
                    } catch (Exception e) {
                        logger.fine("Error shutting down executor: " + e.getMessage());
                    }
                    return true;
                }
                return false;
            });
        }
    }
    
    /**
     * 重置特定语言的执行器（清除状态）
     * 
     * @param language 编程语言
     */
    public void resetExecutor(String language) {
        String normalizedLang = normalizeLanguage(language);
        synchronized (cacheLock) {
            for (Map.Entry<String, REPLExecutor> entry : executorCache.entrySet()) {
                if (entry.getKey().startsWith(normalizedLang + "_")) {
                    entry.getValue().reset();
                }
            }
        }
    }
    
    /**
     * 关闭工厂，释放所有资源
     */
    public void shutdown() {
        clearCache();
    }
}
