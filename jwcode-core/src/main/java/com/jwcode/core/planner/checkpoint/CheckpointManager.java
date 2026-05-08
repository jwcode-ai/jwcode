package com.jwcode.core.planner.checkpoint;

import com.jwcode.core.planner.ExecutionPlan;
import com.jwcode.core.planner.PlanStep;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * CheckpointManager — 任务中断/恢复的检查点管理器。
 *
 * <p>当用户打断任务时，自动保存当前执行上下文到检查点。
 * 当用户要求恢复时，从检查点加载并继续执行。</p>
 *
 * <p>存储路径：{@code .jwcode/checkpoint/{taskId}/}</p>
 */
public class CheckpointManager {

    private static final Logger logger = Logger.getLogger(CheckpointManager.class.getName());

    private static final String CHECKPOINT_DIR = ".jwcode/checkpoint";
    private static final String CONTEXT_FILE = "context.json";
    private static final String RESULTS_FILE = "results.json";
    private static final String BUS_FILE = "bus.json";
    private static final String TIMELINE_FILE = "timeline.json";

    private final Path baseDir;

    public CheckpointManager() {
        String userDir = System.getProperty("user.dir", ".");
        this.baseDir = Paths.get(userDir, CHECKPOINT_DIR);
    }

    public CheckpointManager(Path baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * 保存检查点
     *
     * @param checkpoint 检查点数据
     * @return true 如果保存成功
     */
    public boolean saveCheckpoint(Checkpoint checkpoint) {
        try {
            Path taskDir = baseDir.resolve(checkpoint.getTaskId());
            Files.createDirectories(taskDir);

            // 保存上下文
            saveJson(taskDir.resolve(CONTEXT_FILE), checkpoint.getContextJson());

            // 保存结果
            saveJson(taskDir.resolve(RESULTS_FILE), checkpoint.getResultsJson());

            // 保存共享总线
            saveJson(taskDir.resolve(BUS_FILE), checkpoint.getBusJson());

            // 保存时间线
            saveJson(taskDir.resolve(TIMELINE_FILE), checkpoint.getTimelineJson());

            logger.info("Checkpoint saved: " + taskDir + " (" + checkpoint.getTaskId() + ")");
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save checkpoint: " + checkpoint.getTaskId(), e);
            return false;
        }
    }

    /**
     * 加载检查点
     *
     * @param taskId 任务ID
     * @return 检查点数据，如果不存在返回 null
     */
    public Checkpoint loadCheckpoint(String taskId) {
        try {
            Path taskDir = baseDir.resolve(taskId);
            if (!Files.exists(taskDir)) {
                logger.warning("Checkpoint not found: " + taskId);
                return null;
            }

            String contextJson = loadJson(taskDir.resolve(CONTEXT_FILE));
            String resultsJson = loadJson(taskDir.resolve(RESULTS_FILE));
            String busJson = loadJson(taskDir.resolve(BUS_FILE));
            String timelineJson = loadJson(taskDir.resolve(TIMELINE_FILE));

            if (contextJson == null) {
                logger.warning("Checkpoint corrupted (missing context): " + taskId);
                return null;
            }

            Checkpoint checkpoint = new Checkpoint(
                taskId,
                contextJson,
                resultsJson != null ? resultsJson : "{}",
                busJson != null ? busJson : "{}",
                timelineJson != null ? timelineJson : "[]"
            );

            logger.info("Checkpoint loaded: " + taskId);
            return checkpoint;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load checkpoint: " + taskId, e);
            return null;
        }
    }

    /**
     * 删除检查点
     */
    public boolean deleteCheckpoint(String taskId) {
        try {
            Path taskDir = baseDir.resolve(taskId);
            if (Files.exists(taskDir)) {
                deleteDirectory(taskDir);
                logger.info("Checkpoint deleted: " + taskId);
                return true;
            }
            return false;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to delete checkpoint: " + taskId, e);
            return false;
        }
    }

    /**
     * 列出所有检查点
     */
    public List<String> listCheckpoints() {
        if (!Files.exists(baseDir)) {
            return Collections.emptyList();
        }
        try {
            return Files.list(baseDir)
                .filter(Files::isDirectory)
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to list checkpoints", e);
            return Collections.emptyList();
        }
    }

    /**
     * 检查检查点是否存在
     */
    public boolean checkpointExists(String taskId) {
        return Files.exists(baseDir.resolve(taskId).resolve(CONTEXT_FILE));
    }

    /**
     * 获取检查点信息摘要
     */
    public String getCheckpointSummary(String taskId) {
        Checkpoint cp = loadCheckpoint(taskId);
        if (cp == null) return "No checkpoint found for: " + taskId;

        StringBuilder sb = new StringBuilder();
        sb.append("═══ Checkpoint Summary ═══\n");
        sb.append("Task ID: ").append(taskId).append("\n");
        sb.append("Saved at: ").append(LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("Context size: ").append(cp.getContextJson().length()).append(" chars\n");
        sb.append("Results size: ").append(cp.getResultsJson().length()).append(" chars\n");
        sb.append("Bus entries: ").append(cp.getBusJson().length()).append(" chars\n");
        return sb.toString();
    }

    private void saveJson(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private String loadJson(Path path) throws IOException {
        if (!Files.exists(path)) return null;
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        logger.warning("Failed to delete: " + p);
                    }
                });
        }
    }

    // ==================== 内部数据类 ====================

    /**
     * 检查点数据
     */
    public static class Checkpoint {
        private final String taskId;
        private final String contextJson;
        private final String resultsJson;
        private final String busJson;
        private final String timelineJson;

        public Checkpoint(String taskId, String contextJson, String resultsJson, 
                         String busJson, String timelineJson) {
            this.taskId = taskId;
            this.contextJson = contextJson;
            this.resultsJson = resultsJson;
            this.busJson = busJson;
            this.timelineJson = timelineJson;
        }

        public String getTaskId() { return taskId; }
        public String getContextJson() { return contextJson; }
        public String getResultsJson() { return resultsJson; }
        public String getBusJson() { return busJson; }
        public String getTimelineJson() { return timelineJson; }

        /**
         * 创建检查点构建器
         */
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String taskId;
            private String contextJson = "{}";
            private String resultsJson = "{}";
            private String busJson = "{}";
            private String timelineJson = "[]";

            public Builder taskId(String taskId) { this.taskId = taskId; return this; }
            public Builder contextJson(String contextJson) { this.contextJson = contextJson; return this; }
            public Builder resultsJson(String resultsJson) { this.resultsJson = resultsJson; return this; }
            public Builder busJson(String busJson) { this.busJson = busJson; return this; }
            public Builder timelineJson(String timelineJson) { this.timelineJson = timelineJson; return this; }

            public Checkpoint build() {
                return new Checkpoint(taskId, contextJson, resultsJson, busJson, timelineJson);
            }
        }
    }
}
