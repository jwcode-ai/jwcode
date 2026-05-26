package com.jwcode.core.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jwcode.core.eval.EvalTask.AcceptanceCheck;
import com.jwcode.core.eval.EvalTask.CheckType;
import com.jwcode.core.eval.EvalTask.Difficulty;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * EvalTaskLoader — 评测任务定义加载器。
 *
 * <p>从 YAML 文件加载 EvalTask 定义，支持 classpath 和文件系统路径。
 * YAML 格式示例：</p>
 *
 * <pre>
 * tasks:
 *   - id: SIMPLE_001
 *     name: "创建文件"
 *     difficulty: SIMPLE
 *     capability: tool_call
 *     userPrompt: "创建一个 hello.txt 文件"
 *     acceptanceChecks:
 *       - type: FILE_EXISTS
 *         params: { path: "hello.txt" }
 *     timeoutSeconds: 30
 *     aiEvalEnabled: false
 * </pre>
 */
public class EvalTaskLoader {

    private static final Logger LOG = Logger.getLogger(EvalTaskLoader.class.getName());

    private final ObjectMapper mapper;

    public EvalTaskLoader() {
        this.mapper = new ObjectMapper(new YAMLFactory());
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 从 classpath 资源文件加载任务定义。
     *
     * @param resourcePath classpath 路径，如 "eval-tasks/simple-tasks.yaml"
     * @return 任务列表
     */
    public List<EvalTask> loadFromClasspath(String resourcePath) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            LOG.warning("未找到评测任务文件 (classpath): " + resourcePath);
            return Collections.emptyList();
        }
        try {
            return parseYaml(is);
        } catch (IOException e) {
            LOG.severe("解析评测任务文件失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从文件系统路径加载任务定义。
     *
     * @param filePath 文件路径
     * @return 任务列表
     */
    public List<EvalTask> loadFromFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            LOG.warning("未找到评测任务文件: " + filePath);
            return Collections.emptyList();
        }
        try {
            return parseYaml(Files.newInputStream(file.toPath()));
        } catch (IOException e) {
            LOG.severe("解析评测任务文件失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从目录加载所有 .yaml 文件。
     *
     * @param dirPath 目录路径
     * @return 合并的任务列表
     */
    public List<EvalTask> loadFromDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            LOG.warning("目录不存在: " + dirPath);
            return Collections.emptyList();
        }

        List<EvalTask> allTasks = new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                List<EvalTask> tasks = loadFromFile(f.getAbsolutePath());
                allTasks.addAll(tasks);
                LOG.info("已加载 " + tasks.size() + " 个任务: " + f.getName());
            }
        }
        return allTasks;
    }

    /**
     * 从 classpath 目录加载所有 .yaml 文件。
     *
     * @param classpathDir classpath 目录路径
     * @return 合并的任务列表
     */
    public List<EvalTask> loadAllFromClasspath(String classpathDir) {
        List<EvalTask> allTasks = new ArrayList<>();

        // 尝试加载常见的文件名
        String[] commonNames = {"simple-tasks", "medium-tasks", "complex-tasks",
            "tool-call-tasks", "agent-tasks", "code-tasks", "all-tasks"};

        for (String name : commonNames) {
            List<EvalTask> tasks = loadFromClasspath(classpathDir + "/" + name + ".yaml");
            allTasks.addAll(tasks);
        }

        // 也尝试加载目录下的所有 yaml（如果 classpath 是目录）
        // classpath 目录遍历在运行时不可靠，所以用命名约定
        return allTasks;
    }

    // ==================== 内部解析 ====================

    @SuppressWarnings("unchecked")
    private List<EvalTask> parseYaml(InputStream is) throws IOException {
        Map<String, Object> root = mapper.readValue(is, new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> rawTasks = (List<Map<String, Object>>) root.get("tasks");
        if (rawTasks == null || rawTasks.isEmpty()) {
            return Collections.emptyList();
        }

        List<EvalTask> tasks = new ArrayList<>();
        for (Map<String, Object> raw : rawTasks) {
            try {
                tasks.add(parseSingleTask(raw));
            } catch (Exception e) {
                LOG.warning("跳过无效任务: " + raw.get("id") + " - " + e.getMessage());
            }
        }
        return tasks;
    }

    @SuppressWarnings("unchecked")
    private EvalTask parseSingleTask(Map<String, Object> raw) {
        EvalTask task = new EvalTask();

        task.setId(str(raw, "id"));
        task.setName(str(raw, "name"));
        task.setDescription(str(raw, "description"));
        task.setCapability(str(raw, "capability"));
        task.setUserPrompt(str(raw, "userPrompt"));

        // 难度
        String diff = str(raw, "difficulty");
        if (diff != null) {
            task.setDifficulty(Difficulty.valueOf(diff.toUpperCase()));
        }

        // 超时
        if (raw.containsKey("timeoutSeconds")) {
            task.setTimeoutSeconds(toInt(raw.get("timeoutSeconds")));
        }

        // AI 评审
        if (raw.containsKey("aiEvalEnabled")) {
            task.setAiEvalEnabled(Boolean.TRUE.equals(raw.get("aiEvalEnabled")));
        }

        // 模拟模式跳过
        if (raw.containsKey("skipInMockMode")) {
            task.setSkipInMockMode(Boolean.TRUE.equals(raw.get("skipInMockMode")));
        }

        // 步骤范围
        if (raw.containsKey("expectedMinSteps")) {
            task.setExpectedMinSteps(toInt(raw.get("expectedMinSteps")));
        }
        if (raw.containsKey("expectedMaxSteps")) {
            task.setExpectedMaxSteps(toInt(raw.get("expectedMaxSteps")));
        }

        // 验收检查
        List<Map<String, Object>> rawChecks = (List<Map<String, Object>>) raw.get("acceptanceChecks");
        if (rawChecks != null) {
            for (Map<String, Object> rc : rawChecks) {
                task.addAcceptanceCheck(parseCheck(rc));
            }
        }

        // 标签
        List<String> rawTags = (List<String>) raw.get("tags");
        if (rawTags != null) {
            task.setTags(rawTags);
        }

        // 依赖
        List<String> rawDeps = (List<String>) raw.get("dependsOn");
        if (rawDeps != null) {
            task.setDependsOn(rawDeps);
        }

        return task;
    }

    @SuppressWarnings("unchecked")
    private AcceptanceCheck parseCheck(Map<String, Object> raw) {
        CheckType type = CheckType.valueOf(str(raw, "type"));
        Map<String, Object> params = (Map<String, Object>) raw.get("params");
        return new AcceptanceCheck(type, params);
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static int toInt(Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) return Integer.parseInt((String) v);
        return 0;
    }
}
