package com.jwcode.core.policy;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * 策略文件监控器 — 使用 WatchService 监控策略目录变更并触发自动重载。
 */
public class PolicyReloadWatcher implements Runnable {

    private static final Logger logger = Logger.getLogger(PolicyReloadWatcher.class.getName());

    private final Path policyDir;
    private final Consumer<Path> onChangeCallback;
    private volatile boolean running = true;
    private WatchService watchService;
    private Thread watcherThread;

    public PolicyReloadWatcher(Path policyDir, Consumer<Path> onChangeCallback) {
        this.policyDir = policyDir;
        this.onChangeCallback = onChangeCallback;
    }

    /** 启动后台监控线程 */
    public void start() {
        try {
            Files.createDirectories(policyDir);
            watchService = FileSystems.getDefault().newWatchService();
            policyDir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);

            watcherThread = new Thread(this, "policy-reload-watcher");
            watcherThread.setDaemon(true);
            watcherThread.start();
            logger.info("[PolicyReloadWatcher] 启动策略文件监控: " + policyDir);
        } catch (IOException e) {
            logger.warning("[PolicyReloadWatcher] 启动失败: " + e.getMessage());
        }
    }

    /** 停止监控 */
    public void stop() {
        running = false;
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
        if (watchService != null) {
            try { watchService.close(); } catch (IOException e) {
                logger.finest("Failed to close watch service: " + e.getMessage());
            }
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                WatchKey key = watchService.poll(5, java.util.concurrent.TimeUnit.SECONDS);
                if (key == null) continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    Path filename = (Path) event.context();
                    String name = filename.toString();
                    // 仅关注策略文件（非隐藏文件）
                    if (name.startsWith(".")) continue;
                    if (!name.endsWith(".json") && !name.endsWith(".yaml") && !name.endsWith(".yml")) continue;

                    logger.info("[PolicyReloadWatcher] 检测到策略变更: " + name + " (" + kind + ")");
                    onChangeCallback.accept(policyDir.resolve(filename));
                }
                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warning("[PolicyReloadWatcher] 监控异常: " + e.getMessage());
            }
        }
    }
}
