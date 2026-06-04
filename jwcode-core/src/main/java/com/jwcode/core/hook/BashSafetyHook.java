package com.jwcode.core.hook;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * BashSafetyHook — 拦截危险 shell 命令，安全命令进入 ASK 审批流程。
 *
 * <p>PRE_TOOL_USE 阶段检查 BashTool/PowerShellTool：
 * <ul>
 *   <li>危险命令 (rm -rf /, mkfs, fork bomb, DROP TABLE, etc.) → DENY</li>
 *   <li>普通 shell 命令 → ASK（需用户确认），支持审批指纹缓存免重复确认</li>
 * </ul>
 *
 * <p>安全级 Hook，fail-closed（异常默认拒绝）。</p>
 */
public class BashSafetyHook implements HookExecutor {

    private static final Set<String> TARGET_TOOLS = Set.of("BashTool", "PowerShellTool");

    private static final Pattern DANGEROUS_CMD = Pattern.compile(
        "rm\\s+-rf\\s+/|mkfs\\.|: *\\( *\\) *\\{ *:|DROP\\s+TABLE|DELETE\\s+FROM|" +
        "format\\s+[a-z]:|del\\s+/[fF]\\s+/[sS]|shutdown\\s+-|>\\s*/dev/sd",
        Pattern.CASE_INSENSITIVE);

    @Override
    public CompletableFuture<HookResult> execute(HookContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (context.getToolName() == null
                    || !TARGET_TOOLS.contains(context.getToolName())) {
                    return HookResult.allow("BashSafetyHook");
                }
                String toolInput = context.getToolInput() != null
                    ? context.getToolInput().toString() : "";
                if (DANGEROUS_CMD.matcher(toolInput).find()) {
                    return HookResult.deny("BashSafetyHook",
                        "Dangerous command pattern detected: " + truncate(toolInput, 80));
                }

                // 审批指纹缓存：相同的命令曾经被批准过 → 自动放行
                String fingerprint = normalizeFingerprint(toolInput);
                if (HookApprovalManager.getInstance().isFingerprintCached(fingerprint)) {
                    return HookResult.allow("BashSafetyHook",
                        "Auto-approved (cached fingerprint): " + truncate(toolInput, 80));
                }

                // ASK 审批：将指纹编码到 askPayload 中，审批通过后在 HookApprovalManager 中自动缓存
                String displayText = truncate(toolInput, 100);
                String askPayload = fingerprint + "\n---\n" + displayText;
                return HookResult.ask("BashSafetyHook", askPayload,
                    "审批 shell 命令执行");
            } catch (Exception e) {
                return HookResult.allow("BashSafetyHook", "Hook error, fail-open");
            }
        });
    }

    @Override
    public HookImplementationType getType() { return HookImplementationType.SHELL; }

    @Override
    public String getName() { return "BashSafetyHook"; }

    @Override
    public HookPriority getPriority() { return HookPriority.SECURITY; }

    @Override
    public boolean isFailOpen() { return false; }

    @Override
    public long getTimeoutMs() { return 5000; }

    @Override
    public boolean supportsEvent(HookEventType eventType) {
        return eventType == HookEventType.PRE_TOOL_USE;
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * 标准化命令文本为指纹：去首尾空白、压缩连续空白、转小写。
     */
    static String normalizeFingerprint(String command) {
        if (command == null) return "";
        return command.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}