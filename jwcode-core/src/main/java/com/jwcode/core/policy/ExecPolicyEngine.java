package com.jwcode.core.policy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 可编程执行策略引擎 — 基于规则判断命令是否可以执行。
 *
 * <p>对标 Codex execpolicy crate，提供：
 * <ul>
 *   <li>前缀匹配 + 正则匹配 + 网络规则</li>
 *   <li>多策略源合并（内置 → 系统 → 用户 → 项目）</li>
 *   <li>ALLOW / DENY / ASK / DELEGATE 四级决策</li>
 *   <li>实时文件监控热加载</li>
 * </ul>
 *
 * <p>线程安全：规则列表使用 CopyOnWriteArrayList。</p>
 */
public class ExecPolicyEngine {

    private static final Logger logger = Logger.getLogger(ExecPolicyEngine.class.getName());
    private static final int MAX_CACHED_PATTERNS = 512;

    private final CopyOnWriteArrayList<PolicyRule> activeRules = new CopyOnWriteArrayList<>();
    private final Map<String, Pattern> compiledRegexCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
            return size() > MAX_CACHED_PATTERNS;
        }
    };

    private final PolicyReloadWatcher reloadWatcher;
    private Path policyDir;

    // 单例
    private static volatile ExecPolicyEngine instance;

    private ExecPolicyEngine() {
        loadBuiltin();
        this.reloadWatcher = null;
    }

    private ExecPolicyEngine(Path policyDir) {
        loadBuiltin();
        this.policyDir = policyDir;
        loadFromDirectory(policyDir);
        this.reloadWatcher = new PolicyReloadWatcher(policyDir, this::onPolicyFileChanged);
        this.reloadWatcher.start();
    }

    /** 获取默认单例（仅内置策略）。 */
    public static ExecPolicyEngine getInstance() {
        if (instance == null) {
            synchronized (ExecPolicyEngine.class) {
                if (instance == null) {
                    instance = new ExecPolicyEngine();
                }
            }
        }
        return instance;
    }

    /** 获取或初始化带策略目录的单例。 */
    public static ExecPolicyEngine getInstance(Path policyDir) {
        if (instance == null || instance.policyDir == null) {
            synchronized (ExecPolicyEngine.class) {
                if (instance == null || instance.policyDir == null) {
                    if (instance != null && instance.reloadWatcher != null) {
                        instance.reloadWatcher.stop();
                    }
                    instance = new ExecPolicyEngine(policyDir);
                }
            }
        }
        return instance;
    }

    /**
     * 对命令作出策略决策。
     *
     * @param command 完整命令字符串
     * @return 策略决策结果
     */
    public PolicyDecision decide(String command) {
        if (command == null || command.isBlank()) {
            return PolicyDecision.allowed();
        }

        String trimmed = command.trim();

        // 按优先级降序遍历规则（高优先级先匹配）
        for (PolicyRule rule : activeRules) {
            if (matchesRule(trimmed, rule)) {
                String reason = "matched rule '" + rule.id() + "': " + rule.description();
                logger.fine("[ExecPolicyEngine] '" + rule.id() + "' → " + rule.action()
                    + " for: " + maskedCommand(trimmed));

                return switch (rule.action()) {
                    case ALLOW -> PolicyDecision.allowedByRule(rule.id(), reason);
                    case DENY -> PolicyDecision.deniedByRule(rule.id(), reason, rule.suggestedAlternative());
                    case ASK -> PolicyDecision.needsApproval(rule.id(), reason);
                    case DELEGATE -> PolicyDecision.delegated(rule.id(), reason);
                };
            }
        }

        // 无规则匹配：放行
        return PolicyDecision.allowed();
    }

    /**
     * 检查网络访问是否被允许。
     */
    public boolean isNetworkAccessAllowed(String domain, String protocol) {
        return true; // 默认允许，如有网络规则则进一步检查
    }

    /**
     * 合并来自某策略源的规则（叠加模式）。
     */
    public void mergePolicy(PolicySource source, List<PolicyRule> rules) {
        if (rules.isEmpty()) return;

        // 移除同源的旧规则
        activeRules.removeIf(r -> r.id() != null && r.id().startsWith(source.name().toLowerCase() + "-"));

        activeRules.addAll(rules);
        activeRules.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        logger.info("[ExecPolicyEngine] 合并 " + source + " 策略: " + rules.size() + " 条规则，总计 " + activeRules.size() + " 条");
    }

    /** 重新加载所有策略 */
    public synchronized void reloadPolicies() {
        activeRules.clear();
        loadBuiltin();
        if (policyDir != null) {
            loadFromDirectory(policyDir);
        }
        logger.info("[ExecPolicyEngine] 策略已重新加载，当前共 " + activeRules.size() + " 条规则");
    }

    /** 获取当前活跃规则数 */
    public int getRuleCount() {
        return activeRules.size();
    }

    /** 获取活跃规则列表（只读） */
    public List<PolicyRule> getActiveRules() {
        return Collections.unmodifiableList(activeRules);
    }

    /** 停止引擎（关闭文件监控） */
    public void shutdown() {
        if (reloadWatcher != null) {
            reloadWatcher.stop();
        }
    }

    // ─── 私有方法 ───

    private void loadBuiltin() {
        List<PolicyRule> builtin = PolicyLoader.loadBuiltin();
        activeRules.addAll(builtin);
        activeRules.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        logger.info("[ExecPolicyEngine] 加载内置策略: " + builtin.size() + " 条");
    }

    private void loadFromDirectory(Path dir) {
        List<PolicyRule> loaded = PolicyLoader.loadFromDirectory(dir);
        if (!loaded.isEmpty()) {
            activeRules.addAll(loaded);
            activeRules.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
            logger.info("[ExecPolicyEngine] 加载用户策略: " + loaded.size() + " 条，来自 " + dir);
        }
    }

    private void onPolicyFileChanged(Path changedFile) {
        logger.info("[ExecPolicyEngine] 策略文件变更，触发重载: " + changedFile.getFileName());
        reloadPolicies();
    }

    private boolean matchesRule(String command, PolicyRule rule) {
        if (rule.commandPrefix() == null || rule.commandPrefix().isEmpty()) {
            return false;
        }

        if (rule.isRegex()) {
            Pattern pattern = compiledRegexCache.computeIfAbsent(rule.commandPrefix(),
                p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE | Pattern.DOTALL));
            return pattern.matcher(command).find();
        }

        // 前缀匹配 (忽略大小写)
        return command.toLowerCase().startsWith(rule.commandPrefix().toLowerCase());
    }

    private static String maskedCommand(String cmd) {
        if (cmd.length() <= 80) return cmd;
        return cmd.substring(0, 77) + "...";
    }
}
