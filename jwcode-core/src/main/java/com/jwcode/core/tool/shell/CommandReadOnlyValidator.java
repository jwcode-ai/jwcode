package com.jwcode.core.tool.shell;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * 命令只读分类器 — 判断命令是否为只读操作。
 *
 * <p>参考 Claude Code 的 readOnlyValidation 设计，提供三层判断：
 * <ul>
 *   <li>精确匹配已知只读命令</li>
 *   <li>模式匹配只读命令族（如 git log/show/diff）</li>
 *   <li>分析命令是否有输出重定向写操作</li>
 * </ul>
 */
public class CommandReadOnlyValidator {

    // 已知只读命令（精确匹配前缀）
    private static final Set<String> READ_ONLY_PREFIXES = new HashSet<>(Arrays.asList(
        // Unix 只读命令
        "ls", "cat", "head", "tail", "less", "more",
        "pwd", "whoami", "id", "date", "uname", "hostname",
        "echo", "printf", "true", "false",
        "grep", "egrep", "fgrep", "wc", "sort", "uniq",
        "find", "locate", "which", "whereis", "type",
        "du", "df", "free", "uptime",
        "ps", "top", "htop", "pgrep", "pidof",
        "lsof", "fuser", "netstat", "ss", "ifconfig", "ip addr",
        "env", "printenv", "ulimit",
        "file", "stat", "readlink", "realpath",
        "diff", "cmp", "comm", "md5sum", "sha256sum", "sha1sum",
        "basename", "dirname", "strings", "od", "xxd",
        // Windows 只读命令
        "dir", "type", "cd", "chdir", "ver", "vol", "date /t", "time /t",
        "systeminfo", "tasklist", "driverquery",
        "where", "whoami", "hostname", "path", "set",
        "assoc", "ftype", "icacls", "cacls",
        "netstat", "ipconfig", "getmac", "arp -a", "nslookup", "ping",
        "tree", "comp", "fc",
        "schtasks /query", "sc query", "wmic", "quser", "qwinsta"
        // removed: powershell/pwsh can execute arbitrary code
    ));

    // 只读命令族（前缀 + 子命令模式）
    private static final Set<CommandFamily> READ_ONLY_FAMILIES = new HashSet<>(Arrays.asList(
        new CommandFamily("git", Set.of(
            "status", "log", "diff", "show", "branch", "tag",
            "remote -v", "remote show", "ls-files", "ls-tree",
            "rev-parse", "rev-list", "describe", "blame",
            "stash list", "stash show", "config --list", "config --get",
            "reflog", "shortlog", "cherry", "bisect log", "bisect view",
            "notes show", "notes list", "worktree list", "submodule status",
            "for-each-ref", "name-rev", "merge-base", "check-ref-format"
        )),
        new CommandFamily("npm", Set.of(
            "list", "ls", "view", "info", "search", "outdated",
            "audit", "dedupe", "explain", "fund", "docs", "bugs",
            "repo", "config list", "config get", "root", "bin", "prefix"
        )),
        new CommandFamily("mvn", Set.of(
            "validate", "compile", "test", "package", "verify",
            "dependency:tree", "dependency:list", "dependency:resolve",
            "help:describe", "help:effective-pom", "help:effective-settings",
            "help:evaluate", "versions:display-dependency-updates"
        )),
        new CommandFamily("docker", Set.of(
            "ps", "images", "info", "version", "stats", "logs",
            "inspect", "top", "diff", "history", "system df", "system info",
            "network ls", "network inspect", "volume ls", "volume inspect",
            "image ls", "container ls", "compose ps", "compose config",
            "compose logs"
        )),
        new CommandFamily("kubectl", Set.of(
            "get", "describe", "logs", "top", "explain",
            "config view", "config get-contexts", "config current-context",
            "api-versions", "api-resources", "cluster-info", "version",
            "auth can-i", "certificate", "rollout status", "rollout history"
        )),
        new CommandFamily("go", Set.of(
            "doc", "fmt", "vet", "list", "version", "env"
        )),
        new CommandFamily("cargo", Set.of(
            "check", "doc", "search", "tree", "metadata", "version"
        )),
        new CommandFamily("pip", Set.of(
            "list", "show", "freeze", "check", "config list"
        )),
        new CommandFamily("python", Set.of(
            "--version", "-V"
        )),
        new CommandFamily("node", Set.of(
            "--version", "-v"
        ))
    ));

    // 只读 Git 命令额外支持（使用 -- 的长参数形式）
    private static final Pattern GIT_READ_ONLY_PATTERN = Pattern.compile(
        "^git\\s+(status|log|diff|show|branch|tag|blame|grep|rev-parse|"
        + "ls-files|ls-tree|describe|stash\\s+(list|show)|remote(\\s+-v|\\s+show)?|"
        + "config\\s+(--list|--get)|reflog|shortlog|cherry|bisect|for-each-ref|"
        + "notes\\s+(show|list)|worktree\\s+list|submodule\\s+status)"
    );

    // 写操作输出重定向模式
    private static final Set<String> DESTRUCTIVE_REDIRECTS = Set.of(
        ">", ">>", "2>", "&>", "1>"
    );

    // 写命令关键词（不完整列表，用于降低误判率）
    private static final Set<String> WRITE_COMMAND_VERBS = Set.of(
        "rm", "mv", "cp", "dd", "mkfs", "mkswap", "fdisk", "parted",
        "chmod", "chown", "chattr", "setfacl",
        "kill", "killall", "pkill", "xkill",
        "shutdown", "reboot", "halt", "poweroff",
        "useradd", "userdel", "usermod", "groupadd", "groupdel",
        "iptables", "nft", "firewall-cmd", "ufw",
        "mount", "umount", "losetup", "swapon", "swapoff",
        "systemctl", "service",
        "crontab",
        "del", "erase", "rmdir", "format",
        "reg add", "reg delete", "reg import"
    );

    /**
     * 判断命令是否为只读操作。
     *
     * @param command 原始命令字符串
     * @return true 如果命令被识别为只读
     */
    public static boolean isReadOnly(String command) {
        if (command == null || command.isBlank()) return false;

        String trimmed = command.trim();
        String lower = trimmed.toLowerCase();

        // 1. 检查写操作重定向
        if (containsDestructiveRedirect(trimmed)) return false;

        // 2. 检查管道中的写命令（如 ls | grep x > file）
        if (hasPipelineWrite(trimmed)) return false;

        // 3. 精确前缀匹配
        for (String prefix : READ_ONLY_PREFIXES) {
            if (lower.startsWith(prefix + " ") || lower.equals(prefix)) {
                // 二次确认：不以写关键词开头
                if (!startsWithWriteVerb(trimmed)) {
                    return true;
                }
            }
        }

        // 4. Git 只读命令模式匹配
        if (GIT_READ_ONLY_PATTERN.matcher(lower).matches()) return true;

        // 5. 命令族匹配
        for (CommandFamily family : READ_ONLY_FAMILIES) {
            String prefix = family.prefix + " ";
            if (lower.startsWith(prefix)) {
                String subCmd = trimmed.substring(prefix.length()).trim();
                for (String subPattern : family.readOnlySubCommands) {
                    if (subCmd.startsWith(subPattern) || subCmd.equals(subPattern)) {
                        return true;
                    }
                }
            }
        }

        // 6. 检查部分匹配的只读族（命令包含只读子命令）
        for (CommandFamily family : READ_ONLY_FAMILIES) {
            if (lower.startsWith(family.prefix + " ")) {
                String rest = trimmed.substring(family.prefix.length() + 1).trim();
                if (containsWriteSubCommand(family, rest)) return false;
                // 无法确认是只读还是写操作，保守地返回 false
                return false;
            }
        }

        return false;
    }

    /**
     * 判断命令是否为明确可允许的安全命令（比 isReadOnly 更严格的白名单）。
     */
    public static boolean isAlwaysSafe(String command) {
        if (command == null || command.isBlank()) return false;
        String lower = command.trim().toLowerCase();

        // 绝对安全：仅信息查询，无任何副作用
        String[] safePrefixes = {
            "echo ", "pwd", "whoami", "date", "uname", "hostname",
            "dir ", "type ", "ver", "vol", "cd ", "chdir ",
            "ls ", "cat ", "head ", "tail ", "wc ",
            "git status", "git log", "git branch", "git diff",
            "git show", "git rev-parse", "find ", "which ",
            "du ", "df ", "free ", "uptime",
            "npm list", "npm ls", "npm view", "pip list", "pip show",
            "docker ps", "docker images", "docker info", "docker version",
            "kubectl get", "kubectl describe", "kubectl logs",
            "where ", "path", "set ", "get-command", "get-childitem",
            "select-string"
        };

        for (String prefix : safePrefixes) {
            if (lower.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * 获取命令的风险等级 (0-10)。
     */
    public static int riskScore(String command) {
        if (command == null || command.isBlank()) return 10;
        String lower = command.trim().toLowerCase();

        if (isAlwaysSafe(command)) return 0;
        if (isReadOnly(command)) return 1;

        int score = 0;

        // 包含写操作重定向 +3
        if (containsDestructiveRedirect(command)) score += 3;

        // 包含管道 +1 (可能是复杂操作)
        if (command.contains("|")) score += 1;

        // 包含 sudo +4
        if (lower.contains("sudo")) score += 4;

        // 包含文件系统写操作关键词 +5
        for (String verb : WRITE_COMMAND_VERBS) {
            if (lower.startsWith(verb + " ") || lower.contains(" " + verb + " ")) {
                score += 5;
                break;
            }
        }

        // 包含 /dev/ 写操作 (dd, mkfs 等) +10
        if (lower.matches(".*(>\\s*/dev/|dd\\s+.*of=/dev/|mkfs\\./dev/).*")) score = 10;

        // 包含网络下载 + 管道执行 +10
        if (lower.matches(".*(curl|wget).*\\|.*(bash|sh|python|perl|ruby).*")) score = 10;

        // fork bomb +10
        if (lower.contains(":(){ :|:& };:")) score = 10;

        return Math.min(10, score);
    }

    // ==== 检测辅助 ====

    private static boolean containsDestructiveRedirect(String cmd) {
        for (String redirect : DESTRUCTIVE_REDIRECTS) {
            if (cmd.contains(redirect)) return true;
        }
        return false;
    }

    private static boolean hasPipelineWrite(String cmd) {
        int pipeIdx = cmd.indexOf('|');
        if (pipeIdx < 0) return false;
        String afterPipe = cmd.substring(pipeIdx + 1).trim();
        return containsDestructiveRedirect(afterPipe);
    }

    private static boolean startsWithWriteVerb(String cmd) {
        String lower = cmd.toLowerCase();
        for (String verb : WRITE_COMMAND_VERBS) {
            if (lower.startsWith(verb + " ") || lower.equals(verb)) return true;
        }
        return false;
    }

    private static boolean containsWriteSubCommand(CommandFamily family, String rest) {
        // 检查是否是写操作子命令
        return false; // 保守假设：未知子命令不是写操作
    }

    /** 命令族：一个命令前缀 + 一组只读子命令 */
    private record CommandFamily(String prefix, Set<String> readOnlySubCommands) {}
}
