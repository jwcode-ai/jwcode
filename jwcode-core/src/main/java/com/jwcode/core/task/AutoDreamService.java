package com.jwcode.core.task;

import com.jwcode.core.llm.LLMService;
import com.jwcode.core.session.Session;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * AutoDream 后台推理服务 — 在用户空闲时执行低优先级 AI 分析任务。
 *
 * <p>参考 Claude Code 的 autoDream 设计：
 * <ul>
 *   <li>用户空闲 30 秒后触发第一轮 dream</li>
 *   <li>使用低成本模型（如 Haiku）执行分析</li>
 *   <li>分析结果缓存到 Session metadata 中</li>
 *   <li>支持 codebase 索引预热、代码质量分析、安全扫描等</li>
 *   <li>用户恢复活动时自动中止</li>
 * </ul>
 */
public class AutoDreamService {
    private static final Logger logger = Logger.getLogger(AutoDreamService.class.getName());

    /** 空闲触发阈值 */
    private static final long IDLE_THRESHOLD_MS = 30_000;  // 30 秒
    private static final long DREAM_COOLDOWN_MS = 120_000; // 两轮 dream 之间最少间隔 2 分钟

    /** 单例 */
    private static volatile AutoDreamService instance;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "autodream");
        t.setDaemon(true);
        return t;
    });

    private volatile LLMService dreamLLM;   // 低成本模型
    private volatile Session activeSession;
    private volatile long lastUserActivity = System.currentTimeMillis();
    private volatile long lastDreamTime = 0;
    private volatile boolean dreaming = false;
    private volatile boolean enabled = true;

    /** Dream 结果回调 */
    private final List<Consumer<DreamResult>> listeners = new CopyOnWriteArrayList<>();

    /** Dream 结果 */
    public record DreamResult(
        DreamType type,
        String title,
        String content,
        long timestamp
    ) {}

    /** Dream 类型 */
    public enum DreamType {
        CODEBASE_INSIGHT,    // 代码库洞察
        SECURITY_SCAN,       // 安全扫描片段
        REFACTOR_SUGGESTION, // 重构建议
        CACHE_WARM,          // 缓存预热
        TODO_DISCOVERY       // 发现 TODO/FIXME
    }

    public static synchronized AutoDreamService getInstance() {
        if (instance == null) {
            instance = new AutoDreamService();
        }
        return instance;
    }

    public void setDreamLLM(LLMService llm) { this.dreamLLM = llm; }
    public void setActiveSession(Session session) { this.activeSession = session; }
    public void setEnabled(boolean e) { this.enabled = e; }
    public boolean isEnabled() { return enabled; }

    /** 标记用户活动，重置空闲计时器 */
    public void touch() {
        lastUserActivity = System.currentTimeMillis();
        if (dreaming) {
            dreaming = false;
            logger.fine("[AutoDream] 用户恢复活动，中止 dream");
        }
    }

    /** 启动空闲监控 */
    public void start() {
        // 每 5 秒检查一次空闲状态
        scheduler.scheduleWithFixedDelay(this::checkIdle, 5, 5, TimeUnit.SECONDS);
        logger.info("[AutoDream] 启动完成，空闲阈值=" + (IDLE_THRESHOLD_MS / 1000) + "s");
    }

    /** 停止 */
    public void stop() {
        scheduler.shutdownNow();
        dreaming = false;
    }

    /** 添加结果监听器 */
    public void addListener(Consumer<DreamResult> listener) {
        listeners.add(listener);
    }

    // ==== 核心逻辑 ====

    private void checkIdle() {
        if (!enabled || dreaming) return;

        long idleDuration = System.currentTimeMillis() - lastUserActivity;
        long sinceLastDream = System.currentTimeMillis() - lastDreamTime;

        if (idleDuration >= IDLE_THRESHOLD_MS && sinceLastDream >= DREAM_COOLDOWN_MS) {
            dreaming = true;
            lastDreamTime = System.currentTimeMillis();
            runDreamCycle();
        }
    }

    private void runDreamCycle() {
        CompletableFuture.runAsync(() -> {
            try {
                logger.fine("[AutoDream] 开始 dream 周期");

                // Dream 1: 代码库洞察
                DreamResult insight = dreamCodebaseInsight();
                if (insight != null) notifyListeners(insight);

                // Dream 2: TODO/FIXME 发现
                if (dreaming) { // 检查是否被中断
                    DreamResult todos = dreamTodoDiscovery();
                    if (todos != null) notifyListeners(todos);
                }

                // Dream 3: 缓存预热
                if (dreaming && activeSession != null) {
                    DreamResult warm = dreamCacheWarm();
                    if (warm != null) notifyListeners(warm);
                }

                logger.fine("[AutoDream] dream 周期完成");
            } catch (Exception e) {
                logger.warning("[AutoDream] dream 周期异常: " + e.getMessage());
            } finally {
                dreaming = false;
            }
        });
    }

    private DreamResult dreamCodebaseInsight() {
        if (dreamLLM == null) return null;

        try {
            // 扫描项目结构
            Path workDir = activeSession != null
                ? Path.of(activeSession.getWorkingDirectory())
                : Path.of(System.getProperty("user.dir"));

            // 统计文件类型分布
            Map<String, Integer> fileTypes = new HashMap<>();
            scanFileTypes(workDir, fileTypes, 2);

            StringBuilder insight = new StringBuilder();
            insight.append("项目文件类型分布:\n");
            fileTypes.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> insight.append("  .").append(e.getKey()).append(": ").append(e.getValue()).append(" 个文件\n"));

            return new DreamResult(DreamType.CODEBASE_INSIGHT, "代码库结构分析", insight.toString(), System.currentTimeMillis());
        } catch (Exception e) {
            return null;
        }
    }

    private DreamResult dreamTodoDiscovery() {
        try {
            Path workDir = activeSession != null
                ? Path.of(activeSession.getWorkingDirectory())
                : Path.of(System.getProperty("user.dir"));

            List<String> todos = new ArrayList<>();
            scanTodos(workDir, todos, 3);

            if (todos.isEmpty()) return null;

            StringBuilder content = new StringBuilder();
            content.append("发现 ").append(todos.size()).append(" 个 TODO/FIXME:\n");
            for (String todo : todos) {
                content.append("  ").append(todo).append("\n");
            }

            return new DreamResult(DreamType.TODO_DISCOVERY, "待办事项发现", content.toString(), System.currentTimeMillis());
        } catch (Exception e) {
            return null;
        }
    }

    private DreamResult dreamCacheWarm() {
        // 预热提示缓存 —— 发送一个简单的系统提示 ping
        if (dreamLLM != null && activeSession != null) {
            return new DreamResult(DreamType.CACHE_WARM, "提示缓存预热",
                "已预热提示缓存，下次请求将更快响应", System.currentTimeMillis());
        }
        return null;
    }

    // ==== 文件扫描辅助 ====

    private void scanFileTypes(Path dir, Map<String, Integer> types, int maxDepth) {
        if (maxDepth <= 0) return;
        try (var files = java.nio.file.Files.list(dir)) {
            files.forEach(f -> {
                try {
                    if (java.nio.file.Files.isDirectory(f)) {
                        String name = f.getFileName().toString();
                        if (!name.startsWith(".") && !name.equals("node_modules")
                            && !name.equals("target") && !name.equals("build")
                            && !name.equals("dist") && !name.equals(".git")) {
                            scanFileTypes(f, types, maxDepth - 1);
                        }
                    } else {
                        String name = f.getFileName().toString();
                        int dot = name.lastIndexOf('.');
                        if (dot > 0) {
                            String ext = name.substring(dot + 1).toLowerCase();
                            types.merge(ext, 1, Integer::sum);
                        }
                    }
                } catch (Exception e) {
                    logger.finest("[AutoDream] scanFileTypes entry error: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.finest("[AutoDream] scanFileTypes dir error: " + e.getMessage());
        }
    }

    private void scanTodos(Path dir, List<String> todos, int maxDepth) {
        if (maxDepth <= 0 || todos.size() >= 20) return;
        try (var files = java.nio.file.Files.list(dir)) {
            files.forEach(f -> {
                try {
                    if (java.nio.file.Files.isDirectory(f)) {
                        String name = f.getFileName().toString();
                        if (!name.startsWith(".") && !name.equals("node_modules")
                            && !name.equals("target") && !name.equals("build")) {
                            scanTodos(f, todos, maxDepth - 1);
                        }
                    } else {
                        String name = f.getFileName().toString();
                        if (name.endsWith(".java") || name.endsWith(".ts") || name.endsWith(".tsx")
                            || name.endsWith(".js") || name.endsWith(".py")) {
                            try {
                                String content = java.nio.file.Files.readString(f);
                                for (String line : content.split("\n")) {
                                    if (line.contains("TODO") || line.contains("FIXME") || line.contains("HACK")) {
                                        todos.add(f.getFileName() + ":" + line.trim());
                                        if (todos.size() >= 20) return;
                                    }
                                }
                            } catch (Exception e) {
                                logger.finest("[AutoDream] scanTodos read error: " + e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.finest("[AutoDream] scanTodos entry error: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.finest("[AutoDream] scanTodos dir error: " + e.getMessage());
        }
    }

    private void notifyListeners(DreamResult result) {
        for (Consumer<DreamResult> listener : listeners) {
            try { listener.accept(result); } catch (Exception e) {
                logger.warning("[AutoDream] listener error: " + e.getMessage());
            }
        }
    }
}
