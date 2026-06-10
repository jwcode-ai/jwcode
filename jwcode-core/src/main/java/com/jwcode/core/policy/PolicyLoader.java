package com.jwcode.core.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 策略加载器 — 从磁盘加载策略文件（JSON 和 YAML 格式）。
 */
public class PolicyLoader {

    private static final Logger logger = Logger.getLogger(PolicyLoader.class.getName());
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
        .findAndRegisterModules()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private static final String POLICY_FILE_EXT = ".json";
    private static final String POLICY_YAML_EXT = ".yaml";

    /**
     * 策略文件的反序列化结构。
     */
    public record PolicyFileDef(
        int version,
        List<PolicyRuleDef> policies,
        List<NetworkRuleDef> networkRules
    ) {}

    public record PolicyRuleDef(
        String id,
        String description,
        int priority,
        String commandPrefix,
        boolean isRegex,
        String action,
        List<String> allowedDomains,
        List<String> allowedProtocols,
        String suggestedAlternative
    ) {}

    public record NetworkRuleDef(
        String domain,
        String protocol,
        String action,
        String justification
    ) {}

    /**
     * 从目录加载所有策略文件。
     */
    public static List<PolicyRule> loadFromDirectory(Path policyDir) {
        if (!Files.isDirectory(policyDir)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(policyDir)) {
            return files
                .filter(f -> f.toString().endsWith(POLICY_FILE_EXT) || f.toString().endsWith(POLICY_YAML_EXT))
                .filter(f -> !f.getFileName().toString().startsWith("."))
                .flatMap(f -> {
                    try {
                        return loadFromFile(f).stream();
                    } catch (Exception e) {
                        logger.warning("[PolicyLoader] 加载策略文件失败: " + f + " — " + e.getMessage());
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList());
        } catch (IOException e) {
            logger.warning("[PolicyLoader] 读取策略目录失败: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * 从单个策略文件加载规则。
     */
    public static List<PolicyRule> loadFromFile(Path file) throws IOException {
        String content = Files.readString(file);
        PolicyFileDef def;

        if (file.toString().endsWith(".yaml") || file.toString().endsWith(".yml")) {
            def = yamlMapper.readValue(content, PolicyFileDef.class);
        } else {
            def = jsonMapper.readValue(content, PolicyFileDef.class);
        }

        if (def.policies() == null) {
            return List.of();
        }

        return def.policies().stream()
            .map(PolicyLoader::convertRule)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private static PolicyRule convertRule(PolicyRuleDef def) {
        try {
            PolicyRule.Action action = PolicyRule.Action.valueOf(def.action().toUpperCase());
            return new PolicyRule(
                def.id(),
                def.description() != null ? def.description() : "",
                def.priority(),
                def.commandPrefix() != null ? def.commandPrefix() : "",
                def.isRegex(),
                action,
                def.allowedDomains() != null ? new HashSet<>(def.allowedDomains()) : Set.of(),
                def.allowedProtocols() != null ? new HashSet<>(def.allowedProtocols()) : Set.of(),
                def.suggestedAlternative()
            );
        } catch (IllegalArgumentException e) {
            logger.warning("[PolicyLoader] 跳过无效规则 '" + def.id() + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * 加载内置策略（代码中硬编码的安全底线）。
     */
    public static List<PolicyRule> loadBuiltin() {
        return List.of(
            PolicyRule.denyRegex("builtin-deny-fork-bomb",
                ":.*\\(\\s*\\).*\\{.*:.*\\|.*:.*&.*\\}.*;.*:",
                1000, "拒绝 fork bomb 模式", "使用安全的并发方式"),
            PolicyRule.deny("builtin-deny-rm-rf-root",
                "rm -rf /", 1000,
                "拒绝删除根目录", "使用更精确的路径"),
            PolicyRule.deny("builtin-deny-mkfs",
                "mkfs", 1000,
                "拒绝格式化文件系统", null),
            PolicyRule.denyRegex("builtin-deny-curl-pipe-bash",
                "curl.*\\|.*(?:ba)?sh", 900,
                "拒绝 curl|bash 管道模式", "先下载脚本检查内容再执行"),
            PolicyRule.denyRegex("builtin-deny-wget-pipe-bash",
                "wget.*\\|.*(?:ba)?sh", 900,
                "拒绝 wget|bash 管道模式", "先下载脚本检查内容再执行"),
            PolicyRule.deny("builtin-deny-chmod-777",
                "chmod -R 777", 800,
                "拒绝危险权限修改", "使用更精确的权限设置"),
            PolicyRule.deny("builtin-deny-taskkill",
                "taskkill", 850,
                "拒绝终止进程", "通过任务管理器手动管理进程"),
            PolicyRule.deny("builtin-deny-wmic-process-delete",
                "wmic process", 850,
                "拒绝通过 WMIC 终止进程", "通过任务管理器手动管理进程"),
            PolicyRule.deny("builtin-deny-stop-process",
                "Stop-Process", 850,
                "拒绝通过 PowerShell 终止进程", "通过任务管理器手动管理进程"),
            PolicyRule.ask("builtin-ask-sudo",
                "sudo", 700,
                "sudo 命令需要审批"),
            PolicyRule.ask("builtin-ask-pip-install",
                "pip install", 500,
                "pip install 需要审批"),
            PolicyRule.ask("builtin-ask-npm-install-global",
                "npm install -g", 500,
                "全局 npm install 需要审批"),
            PolicyRule.allow("builtin-allow-git-status",
                "git status", 100, "Git 状态查询始终允许"),
            PolicyRule.allow("builtin-allow-git-log",
                "git log", 100, "Git 日志查询始终允许"),
            PolicyRule.allow("builtin-allow-git-diff",
                "git diff", 100, "Git diff 始终允许"),
            PolicyRule.allow("builtin-allow-ls",
                "ls ", 50, "ls 始终允许"),
            PolicyRule.allow("builtin-allow-dir",
                "dir ", 50, "Windows dir 始终允许"),
            PolicyRule.allow("builtin-allow-cat",
                "cat ", 50, "cat 始终允许"),
            PolicyRule.allow("builtin-allow-type",
                "type ", 50, "Windows type 始终允许"),
            PolicyRule.allow("builtin-allow-echo",
                "echo ", 50, "echo 始终允许"),
            PolicyRule.allow("builtin-allow-pwd",
                "pwd", 50, "pwd 始终允许"),
            PolicyRule.allow("builtin-allow-find",
                "find ", 50, "find 始终允许"),
            PolicyRule.allow("builtin-allow-grep",
                "grep ", 50, "grep 始终允许")
        );
    }
}
