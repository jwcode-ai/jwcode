package com.jwcode.core.config;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

@DisplayName("PromptSectionCache — 提示段落缓存测试")
class PromptSectionCacheTest {

    private PromptSectionCache cache;

    @BeforeEach
    void setUp() {
        cache = new PromptSectionCache();
    }

    @Test
    @DisplayName("首次调用计算并缓存")
    void firstCallComputes() {
        AtomicInteger computeCount = new AtomicInteger(0);
        String result1 = cache.getOrCompute("test", "v1", 0, () -> {
            computeCount.incrementAndGet();
            return "computed content";
        });
        assertEquals("computed content", result1);
        assertEquals(1, computeCount.get());
    }

    @Test
    @DisplayName("相同 versionToken 返回缓存")
    void sameVersionTokenUsesCache() {
        AtomicInteger computeCount = new AtomicInteger(0);
        cache.getOrCompute("test", "v1", 0, () -> {
            computeCount.incrementAndGet();
            return "content v1";
        });
        String result2 = cache.getOrCompute("test", "v1", 0, () -> {
            computeCount.incrementAndGet();
            return "content v1 again";
        });
        assertEquals("content v1", result2);
        assertEquals(1, computeCount.get()); // 只计算了一次
    }

    @Test
    @DisplayName("不同 versionToken 重新计算")
    void differentVersionTokenRecomputes() {
        AtomicInteger computeCount = new AtomicInteger(0);
        cache.getOrCompute("test", "v1", 0, () -> {
            computeCount.incrementAndGet();
            return "content v1";
        });
        String result2 = cache.getOrCompute("test", "v2", 0, () -> {
            computeCount.incrementAndGet();
            return "content v2";
        });
        assertEquals("content v2", result2);
        assertEquals(2, computeCount.get());
    }

    @Test
    @DisplayName("TTL 过期后重新计算")
    void ttlExpiryRecomputes() throws InterruptedException {
        AtomicInteger computeCount = new AtomicInteger(0);
        cache.getOrCompute("ttl_test", "v1", 1, () -> {
            computeCount.incrementAndGet();
            return "first";
        });
        Thread.sleep(10); // 等待 TTL 过期（1ms TTL）
        cache.getOrCompute("ttl_test", "v1", 1, () -> {
            computeCount.incrementAndGet();
            return "second";
        });
        assertEquals(2, computeCount.get());
    }

    @Test
    @DisplayName("invalidate 清除指定 section")
    void invalidateClearsSection() {
        AtomicInteger computeCount = new AtomicInteger(0);
        cache.getOrCompute("core", "v1", 0, () -> {
            computeCount.incrementAndGet();
            return "core content";
        });
        cache.getOrCompute("rules", "v1", 0, () -> {
            computeCount.incrementAndGet();
            return "rules content";
        });
        assertEquals(2, computeCount.get());

        cache.invalidate("core");
        cache.getOrCompute("core", "v1", 0, () -> {
            computeCount.incrementAndGet();
            return "core content new";
        });
        assertEquals(3, computeCount.get()); // core 重新计算

        // rules 仍然缓存
        cache.getOrCompute("rules", "v1", 0, () -> {
            computeCount.incrementAndGet();
            return "should not compute";
        });
        assertEquals(3, computeCount.get());
    }

    @Test
    @DisplayName("invalidateAll 清除所有缓存")
    void invalidateAllClearsEverything() {
        AtomicInteger computeCount = new AtomicInteger(0);
        cache.getOrCompute("a", "v1", 0, () -> { computeCount.incrementAndGet(); return "a"; });
        cache.getOrCompute("b", "v1", 0, () -> { computeCount.incrementAndGet(); return "b"; });

        cache.invalidateAll();

        cache.getOrCompute("a", "v1", 0, () -> { computeCount.incrementAndGet(); return "a2"; });
        cache.getOrCompute("b", "v1", 0, () -> { computeCount.incrementAndGet(); return "b2"; });
        assertEquals(4, computeCount.get());
    }
}
