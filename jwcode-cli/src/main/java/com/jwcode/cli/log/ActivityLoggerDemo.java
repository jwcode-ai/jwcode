package com.jwcode.cli.log;

/**
 * ActivityLogger 演示程序
 * 
 * 展示 AI 活动日志系统的各种功能
 */
public class ActivityLoggerDemo {
    
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║        AI Activity Logger 演示                               ║");
        System.out.println("║        类似 KimiCode 的实时活动显示                          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        // 获取日志实例
        ActivityLogger logger = ActivityLogger.getInstance();
        logger.setCompactMode(true);  // 紧凑模式
        logger.setUseColor(true);     // 使用颜色
        
        // 演示 1: 文件操作
        demoFileOperations();
        
        // 演示 2: 代码搜索
        demoCodeSearch();
        
        // 演示 3: 命令执行
        demoCommandExecution();
        
        // 演示 4: 网络操作
        demoWebOperations();
        
        // 演示 5: AI 思考过程
        demoAIThinking();
        
        // 演示 6: 带进度的操作
        demoProgressOperations();
        
        // 演示 7: 错误处理
        demoErrorHandling();
        
        // 演示 8: 详细模式
        demoDetailedMode();
        
        // 显示统计
        showStatistics();
    }
    
    private static void demoFileOperations() {
        System.out.println();
        CliLogger.getInstance().title("演示 1: 文件操作");
        
        // 读取文件
        String readId = CliLogger.readingFile("src/main/java/com/example/Main.java");
        sleep(500);
        CliLogger.done(readId, "256 行");
        
        // 搜索文件
        String searchId = CliLogger.searchingFiles("**/*.java");
        sleep(300);
        CliLogger.done(searchId, "找到 42 个文件");
        
        // 编辑文件
        String editId = CliLogger.editingFile("src/main/java/com/example/Config.java");
        sleep(400);
        CliLogger.done(editId, "已修改 3 处");
        
        // 写入文件
        String writeId = CliLogger.writingFile("docs/README.md");
        sleep(300);
        CliLogger.done(writeId, "1.2 KB");
    }
    
    private static void demoCodeSearch() {
        System.out.println();
        CliLogger.getInstance().title("演示 2: 代码搜索");
        
        // 搜索代码
        String search1 = CliLogger.searchingCode("class ActivityLogger", "src");
        sleep(400);
        CliLogger.done(search1, "5 个匹配");
        
        // 再次搜索
        String search2 = CliLogger.searchingCode("CompletableFuture", null);
        sleep(300);
        CliLogger.done(search2, "12 个匹配");
    }
    
    private static void demoCommandExecution() {
        System.out.println();
        CliLogger.getInstance().title("演示 3: 命令执行");
        
        // 执行命令
        String cmd1 = CliLogger.executingCommand("mvn clean compile");
        sleep(800);
        CliLogger.done(cmd1, "BUILD SUCCESS");
        
        // Git 命令
        String cmd2 = CliLogger.executingCommand("git status");
        sleep(300);
        CliLogger.done(cmd2, "nothing to commit");
    }
    
    private static void demoWebOperations() {
        System.out.println();
        CliLogger.getInstance().title("演示 4: 网络操作");
        
        // 网络搜索
        String web1 = CliLogger.webSearching("Java CompletableFuture tutorial");
        sleep(600);
        CliLogger.done(web1, "10 个结果");
        
        // 再次搜索
        String web2 = CliLogger.webSearching("Spring Boot best practices 2024");
        sleep(500);
        CliLogger.done(web2, "8 个结果");
    }
    
    private static void demoAIThinking() {
        System.out.println();
        CliLogger.getInstance().title("演示 5: AI 思考过程");
        
        // AI 规划
        String planId = ActivityLogger.start(ActivityType.AI_PLANNING, 
            "分析需求并制定实现方案");
        sleep(300);
        ActivityLogger.progress(planId, 30);
        sleep(200);
        ActivityLogger.progress(planId, 60);
        sleep(200);
        ActivityLogger.progress(planId, 90);
        sleep(200);
        ActivityLogger.complete(planId, "制定完成：3 个步骤");
        
        // AI 分析
        String analyzeId = ActivityLogger.start(ActivityType.AI_ANALYZING, 
            "分析代码结构");
        sleep(400);
        ActivityLogger.complete(analyzeId, "发现 2 个类，15 个方法");
    }
    
    private static void demoProgressOperations() {
        System.out.println();
        CliLogger.getInstance().title("演示 6: 带进度的操作");
        
        // 模拟文件读取进度
        String progressId = ActivityLogger.start(ActivityType.FILE_READ, 
            "读取大文件 data.json");
        
        for (int i = 0; i <= 100; i += 20) {
            ActivityLogger.progress(progressId, i);
            sleep(200);
        }
        
        ActivityLogger.complete(progressId, "读取完成 2.5 MB");
    }
    
    private static void demoErrorHandling() {
        System.out.println();
        CliLogger.getInstance().title("演示 7: 错误处理");
        
        // 失败的读取
        String fail1 = CliLogger.readingFile("/nonexistent/file.txt");
        sleep(300);
        CliLogger.failed(fail1, "文件不存在: /nonexistent/file.txt");
        
        // 失败的命令
        String fail2 = CliLogger.executingCommand("invalid_command");
        sleep(300);
        CliLogger.failed(fail2, "命令未找到: invalid_command");
    }
    
    private static void demoDetailedMode() {
        System.out.println();
        CliLogger.getInstance().title("演示 8: 详细模式");
        
        ActivityLogger logger = ActivityLogger.getInstance();
        logger.setCompactMode(false);
        
        // 详细模式下的操作
        String detailId = logger.startActivity(ActivityType.CODE_REFACTOR, 
            "重构 UserService 类");
        
        logger.addActivityMetadata(detailId, "targetClass", "UserService");
        logger.addActivityMetadata(detailId, "refactoringType", "Extract Method");
        
        sleep(300);
        logger.updateProgress(detailId, 50, "提取方法中...");
        sleep(300);
        
        logger.complete(detailId, "成功提取 2 个方法");
        
        // 恢复紧凑模式
        logger.setCompactMode(true);
    }
    
    private static void showStatistics() {
        System.out.println();
        System.out.println();
        CliLogger.getInstance().title("活动统计");
        
        ActivityLogger.ActivityStats stats = ActivityLogger.getInstance().getStats();
        
        CliLogger log = CliLogger.getInstance();
        log.info("总活动数: " + stats.getTotalActivities());
        log.success("成功: " + stats.getSuccessfulActivities());
        log.error("失败: " + stats.getFailedActivities());
        log.info("成功率: " + String.format("%.1f%%", stats.getSuccessRate()));
        log.info("总耗时: " + stats.getTotalDurationMs() + "ms");
        
        System.out.println();
        System.out.println("演示完成！");
    }
    
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
