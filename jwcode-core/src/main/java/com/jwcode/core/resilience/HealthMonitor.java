package com.jwcode.core.resilience;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 健康监控器
 * 
 * 监控系统运行状态，包括内存、线程、API 健康等
 */
@Slf4j
public class HealthMonitor {
    
    private static final long DEFAULT_INTERVAL_MS = 30000; // 30秒
    
    private final ScheduledExecutorService scheduler;
    private final List<HealthCheck> checks = new CopyOnWriteArrayList<>();
    private final Map<String, HealthStatus> lastStatuses = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<HealthListener> listeners = new CopyOnWriteArrayList<>();
    
    public HealthMonitor() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HealthMonitor");
            t.setDaemon(true);
            return t;
        });
        
        // 注册默认检查
        registerDefaultChecks();
    }
    
    /**
     * 启动监控
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(
                this::runChecks,
                0,
                DEFAULT_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );
            log.info("[HealthMonitor] 监控已启动");
        }
    }
    
    /**
     * 停止监控
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("[HealthMonitor] 监控已停止");
        }
    }
    
    /**
     * 注册健康检查
     */
    public void registerCheck(HealthCheck check) {
        checks.add(check);
    }
    
    /**
     * 添加监听器
     */
    public void addListener(HealthListener listener) {
        listeners.add(listener);
    }
    
    /**
     * 执行所有检查
     */
    private void runChecks() {
        for (HealthCheck check : checks) {
            try {
                HealthStatus status = check.check();
                HealthStatus previous = lastStatuses.put(check.getName(), status);
                
                // 状态变化时通知
                if (previous == null || previous.getStatus() != status.getStatus()) {
                    notifyListeners(check.getName(), status, previous);
                }
                
            } catch (Exception e) {
                log.error("[HealthMonitor] 检查 " + check.getName() + " 失败: " + e.getMessage());
            }
        }
    }
    
    private void notifyListeners(String checkName, HealthStatus current, HealthStatus previous) {
        for (HealthListener listener : listeners) {
            try {
                listener.onStatusChange(checkName, current, previous);
            } catch (Exception e) {
                log.error("[HealthMonitor] 监听器异常: " + e.getMessage());
            }
        }
    }
    
    /**
     * 获取整体健康状态
     */
    public SystemHealth getSystemHealth() {
        boolean allHealthy = lastStatuses.values().stream()
            .allMatch(s -> s.getStatus() == HealthStatus.Status.HEALTHY);
        
        return SystemHealth.builder()
            .healthy(allHealthy)
            .checkCount(checks.size())
            .statuses(new HashMap<>(lastStatuses))
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * 生成健康报告
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("╔════════════════════════════════════════════════════════╗\n");
        report.append("║              系统健康报告                               ║\n");
        report.append("╚════════════════════════════════════════════════════════╝\n\n");
        
        SystemHealth health = getSystemHealth();
        String statusIcon = health.isHealthy() ? "✓" : "✗";
        report.append("整体状态: ").append(statusIcon).append(health.isHealthy() ? " 健康" : " 异常").append("\n\n");
        
        for (Map.Entry<String, HealthStatus> entry : health.getStatuses().entrySet()) {
            HealthStatus status = entry.getValue();
            String icon = status.getStatus() == HealthStatus.Status.HEALTHY ? "✓" : 
                         (status.getStatus() == HealthStatus.Status.DEGRADED ? "~" : "✗");
            
            report.append(String.format("%s %-20s %s\n", 
                icon, entry.getKey(), status.getStatus()));
            
            if (status.getMessage() != null) {
                report.append("   ").append(status.getMessage()).append("\n");
            }
        }
        
        return report.toString();
    }
    
    /**
     * 注册默认检查
     */
    private void registerDefaultChecks() {
        // 内存检查
        registerCheck(new HealthCheck() {
            @Override
            public String getName() { return "memory"; }
            
            @Override
            public HealthStatus check() {
                MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
                MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
                long used = heapUsage.getUsed();
                long max = heapUsage.getMax();
                double usagePercent = (double) used / max * 100;
                
                if (usagePercent > 90) {
                    return HealthStatus.unhealthy("内存使用率过高: " + String.format("%.1f%%", usagePercent));
                } else if (usagePercent > 75) {
                    return HealthStatus.degraded("内存使用率较高: " + String.format("%.1f%%", usagePercent));
                }
                return HealthStatus.healthy("内存正常: " + String.format("%.1f%%", usagePercent));
            }
        });
        
        // 线程检查
        registerCheck(new HealthCheck() {
            @Override
            public String getName() { return "threads"; }
            
            @Override
            public HealthStatus check() {
                ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                int threadCount = threadMXBean.getThreadCount();
                int peakCount = threadMXBean.getPeakThreadCount();
                
                if (threadCount > 500) {
                    return HealthStatus.degraded("线程数较多: " + threadCount);
                }
                return HealthStatus.healthy("线程数: " + threadCount + " (峰值: " + peakCount + ")");
            }
        });
        
        // GC 检查
        registerCheck(new HealthCheck() {
            @Override
            public String getName() { return "gc"; }
            
            @Override
            public HealthStatus check() {
                List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
                long totalCollections = 0;
                long totalTime = 0;
                
                for (GarbageCollectorMXBean gcBean : gcBeans) {
                    totalCollections += gcBean.getCollectionCount();
                    totalTime += gcBean.getCollectionTime();
                }
                
                if (totalTime > 10000) { // GC 时间超过 10 秒
                    return HealthStatus.degraded("GC 累计时间较高: " + totalTime + "ms");
                }
                return HealthStatus.healthy("GC 次数: " + totalCollections + ", 时间: " + totalTime + "ms");
            }
        });
    }
    
    // ==================== 接口和类 ====================
    
    public interface HealthCheck {
        String getName();
        HealthStatus check();
    }
    
    public interface HealthListener {
        void onStatusChange(String checkName, HealthStatus current, HealthStatus previous);
    }
    
    @Data
    @Builder
    public static class HealthStatus {
        private Status status;
        private String message;
        private long timestamp;
        
        public enum Status {
            HEALTHY,    // 健康
            DEGRADED,   // 降级
            UNHEALTHY   // 不健康
        }
        
        public static HealthStatus healthy(String message) {
            return HealthStatus.builder()
                .status(Status.HEALTHY)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
        }
        
        public static HealthStatus degraded(String message) {
            return HealthStatus.builder()
                .status(Status.DEGRADED)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
        }
        
        public static HealthStatus unhealthy(String message) {
            return HealthStatus.builder()
                .status(Status.UNHEALTHY)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
        }
    }
    
    @Data
    @Builder
    public static class SystemHealth {
        private boolean healthy;
        private int checkCount;
        private Map<String, HealthStatus> statuses;
        private long timestamp;
    }
}
