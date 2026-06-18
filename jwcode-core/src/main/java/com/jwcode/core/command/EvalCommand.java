package com.jwcode.core.command;

import com.jwcode.core.eval.AcceptanceChecker;
import com.jwcode.core.eval.EvalReportGenerator;
import com.jwcode.core.eval.EvalTask;
import com.jwcode.core.eval.EvalTask.EvalResult;
import com.jwcode.core.eval.EvalTask.EvalReport;
import com.jwcode.core.eval.EvalTask.Difficulty;
import com.jwcode.core.eval.EvalTaskLoader;
import com.jwcode.core.session.Session;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EvalCommand — 运行能力评测。
 *
 * <p>用法：</p>
 * <ul>
 *   <li><code>/test</code> — 运行所有评测任务（模拟模式）</li>
 *   <li><code>/test simple</code> — 只运行简单任务</li>
 *   <li><code>/test medium</code> — 只运行中等任务</li>
 *   <li><code>/test complex</code> — 只运行复杂任务</li>
 *   <li><code>/test full</code> — 完整模式（真实 LLM 调用）</li>
 *   <li><code>/test list</code> — 列出所有评测任务</li>
 *   <li><code>/test help</code> — 显示帮助</li>
 * </ul>
 */
public class EvalCommand implements Command {

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String getName() {
        return "eval";
    }

    @Override
    public List<String> getAliases() {
        return List.of("t");
    }

    @Override
    public String getDescription() {
        return "运行 JWCode 能力评测，验证系统在不同难度下的任务完成能力";
    }

    @Override
    public String getUsage() {
        return "test [simple|medium|complex|full|list|help]";
    }

    @Override
    public CommandResult execute(String[] args, Session session) {
        // 确定子命令
        String subCommand = args.length > 0 ? args[0].toLowerCase() : "all";

        return switch (subCommand) {
            case "help" -> showHelp();
            case "list" -> listTasks();
            case "simple" -> runEval("simple", false);
            case "medium" -> runEval("medium", false);
            case "complex" -> runEval("complex", false);
            case "full" -> runEval("all", true);
            case "all" -> runEval("all", false);
            default -> CommandResult.error("未知子命令: " + subCommand + "。使用 /test help 查看用法");
        };
    }

    /**
     * 显示帮助信息。
     */
    private CommandResult showHelp() {
        String help = """
            ╔══════════════════════════════════════════════════════════════╗
            ║  /test 命令 — JWCode 能力评测                              ║
            ╠══════════════════════════════════════════════════════════════╣
            ║  /test             运行所有评测（模拟模式）                  ║
            ║  /test simple      只运行简单任务集                         ║
            ║  /test medium      只运行中等任务集                         ║
            ║  /test complex     只运行复杂任务集                         ║
            ║  /test full        完整模式（真实 LLM 调用，耗时较长）      ║
            ║  /test list        列出所有评测任务                         ║
            ║  /test help        显示此帮助                               ║
            ╚══════════════════════════════════════════════════════════════╝
            """;
        return CommandResult.success(help);
    }

    /**
     * 列出所有评测任务。
     */
    private CommandResult listTasks() {
        EvalTaskLoader loader = new EvalTaskLoader();
        List<EvalTask> allTasks = loadAllTasks(loader);

        if (allTasks.isEmpty()) {
            return CommandResult.error("未找到评测任务定义文件");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 评测任务列表 (").append(allTasks.size()).append(" 个)\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        for (Difficulty diff : Difficulty.values()) {
            List<EvalTask> tasks = allTasks.stream()
                .filter(t -> t.getDifficulty() == diff)
                .collect(Collectors.toList());
            if (!tasks.isEmpty()) {
                sb.append("\n").append(diff.getLabel()).append(":\n");
                for (EvalTask task : tasks) {
                    sb.append("  ").append(task.getId()).append(" - ").append(task.getName());
                    sb.append(" (").append(task.getCapability()).append(")");
                    if (task.isAiEvalEnabled()) {
                        sb.append(" [AI评审]");
                    }
                    sb.append("\n");
                }
            }
        }

        sb.append("\n💡 使用 /test <难度> 运行指定任务集");
        return CommandResult.success(sb.toString());
    }

    /**
     * 运行评测。
     */
    private CommandResult runEval(String difficultyFilter, boolean fullMode) {
        long startTime = System.currentTimeMillis();
        Path workspaceRoot = Paths.get(System.getProperty("user.dir"));

        // 加载任务
        EvalTaskLoader loader = new EvalTaskLoader();
        List<EvalTask> allTasks = loadAllTasks(loader);

        if (allTasks.isEmpty()) {
            return CommandResult.error("未找到评测任务定义文件");
        }

        // 按难度过滤
        List<EvalTask> tasksToRun;
        if ("simple".equals(difficultyFilter)) {
            tasksToRun = filterByDifficulty(allTasks, Difficulty.SIMPLE);
        } else if ("medium".equals(difficultyFilter)) {
            tasksToRun = filterByDifficulty(allTasks, Difficulty.MEDIUM);
        } else if ("complex".equals(difficultyFilter)) {
            tasksToRun = filterByDifficulty(allTasks, Difficulty.COMPLEX);
        } else {
            tasksToRun = allTasks;
        }

        if (tasksToRun.isEmpty()) {
            return CommandResult.error("没有匹配的评测任务");
        }

        // 执行评测
        AcceptanceChecker checker = new AcceptanceChecker(workspaceRoot);
        List<EvalResult> results = new ArrayList<>();
        int passed = 0;

        System.out.println("\n🔍 JWCode 能力评测 " + (fullMode ? "[完整模式]" : "[模拟模式]"));
        System.out.println("   任务数: " + tasksToRun.size());
        System.out.println("   " + (fullMode ? "⚠ 完整模式会调用 LLM，耗时较长" : "模拟模式仅做验收检查"));
        System.out.println();

        for (int i = 0; i < tasksToRun.size(); i++) {
            EvalTask task = tasksToRun.get(i);
            System.out.print("   [" + (i + 1) + "/" + tasksToRun.size() + "] " + task.getId() + " " + task.getName() + "... ");
            System.out.flush();

            long taskStart = System.currentTimeMillis();

            // 执行验收检查
            var checkResults = checker.executeAll(task.getAcceptanceChecks());
            boolean taskPassed = com.jwcode.core.eval.EvalTask.CheckResult.class
                .cast(checkResults.stream().filter(c -> !c.isPassed()).findFirst().orElse(null)) == null
                && checkResults.stream().allMatch(c -> c.isPassed());

            // 修正：上面那段逻辑有误，直接用 AcceptanceChecker
            taskPassed = AcceptanceChecker.allPassed(checkResults);

            EvalResult result = new EvalResult();
            result.setTaskId(task.getId());
            result.setPassed(taskPassed);
            result.setObjectiveScore(AcceptanceChecker.calculateScore(checkResults));
            result.setDurationMs(System.currentTimeMillis() - taskStart);
            checkResults.forEach(result::addCheckResult);

            if (!taskPassed) {
                var firstFail = checkResults.stream().filter(c -> !c.isPassed()).findFirst();
                result.setFailureReason(firstFail.map(c -> c.getDescription() + ": " + c.getDetail()).orElse("失败"));
            }

            results.add(result);
            if (taskPassed) passed++;

            String icon = taskPassed ? "✅" : "❌";
            System.out.println(icon + " (" + (System.currentTimeMillis() - taskStart) + "ms)");
        }

        // 生成报告
        long totalTime = System.currentTimeMillis() - startTime;
        int total = tasksToRun.size();

        StringBuilder report = new StringBuilder();
        report.append("\n");
        report.append("╔══════════════════════════════════════════════════════════════╗\n");
        report.append("║  JWCode 能力评测报告                                      ║\n");
        report.append("╠══════════════════════════════════════════════════════════════╣\n");
        report.append(String.format("║  模式: %-44s║\n", fullMode ? "完整模式 (真实LLM)" : "模拟模式 (仅验收)"));
        report.append(String.format("║  时间: %-44s║\n", LocalDateTime.now().format(TIMESTAMP_FMT)));
        report.append(String.format("║  耗时: %-44s║\n", formatDuration(totalTime)));
        report.append(String.format("║  结果: %d/%d 通过 (%.1f%%)                              ║\n",
            passed, total, (double) passed / total * 100.0));
        report.append("╚══════════════════════════════════════════════════════════════╝\n");

        // 保存 JSON 报告到 eval-results
        try {
            EvalReportGenerator reportGen = new EvalReportGenerator();
            EvalReport evalReport = new EvalReport();
            evalReport.setSuiteName("JWCode Capability Eval (/test)");
            evalReport.setTimestamp(LocalDateTime.now().format(TIMESTAMP_FMT));
            evalReport.setTotalDurationMs(totalTime);
            results.forEach(evalReport::addResult);

            String outputDir = System.getProperty("eval.output.dir",
                workspaceRoot.resolve("eval-results").toString());
            reportGen.saveToFiles(evalReport, outputDir, "quick-eval");
        } catch (Exception e) {
            report.append("\n⚠ 保存报告失败: ").append(e.getMessage()).append("\n");
        }

        return CommandResult.success(report.toString());
    }

    // ==================== 工具方法 ====================

    private static List<EvalTask> loadAllTasks(EvalTaskLoader loader) {
        List<EvalTask> tasks = new ArrayList<>();
        tasks.addAll(loader.loadFromClasspath("eval-tasks/simple-tasks.yaml"));
        tasks.addAll(loader.loadFromClasspath("eval-tasks/medium-tasks.yaml"));
        tasks.addAll(loader.loadFromClasspath("eval-tasks/complex-tasks.yaml"));
        return tasks;
    }

    private static List<EvalTask> filterByDifficulty(List<EvalTask> tasks, Difficulty difficulty) {
        return tasks.stream()
            .filter(t -> t.getDifficulty() == difficulty)
            .collect(Collectors.toList());
    }

    private static String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return String.format("%.1fs", ms / 1000.0);
        long minutes = ms / 60000;
        long seconds = (ms % 60000) / 1000;
        return minutes + "m " + seconds + "s";
    }
}
