package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * DoctorCommand - 系统诊断命令
 * 
 * 功能说明：
 * 检查系统配置、环境设置和依赖项，诊断潜在问题。
 * 类似于 npm doctor 或 brew doctor 命令。
 * 
 * 检查项目：
 * - Java 版本
 * - 内存配置
 * - 文件系统权限
 * - 配置文件有效性
 * - 网络连接
 * - 环境变量
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class DoctorCommand implements Command {
    
    @Override
    public String getName() {
        return "doctor";
    }
    
    @Override
    public String getDescription() {
        return "诊断系统配置和环境问题。检查 Java 版本、内存配置、文件权限、配置文件等。";
    }
    
    @Override
    public String getUsage() {
        return "/doctor [--verbose]";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        boolean verbose = args.contains("--verbose") || args.contains("-v");
        
        StringBuilder output = new StringBuilder();
        output.append("╔══════════════════════════════════════════════════════════╗\n");
        output.append("║              JWCode 系统诊断报告                         ║\n");
        output.append("╚══════════════════════════════════════════════════════════╝\n\n");
        
        List<CheckResult> results = new ArrayList<>();
        
        // 执行各项检查
        results.add(checkJavaVersion());
        results.add(checkMemoryConfig());
        results.add(checkFileSystemPermissions());
        results.add(checkConfigFiles());
        results.add(checkEnvironmentVariables());
        results.add(checkNetworkConnectivity());
        
        if (verbose) {
            results.add(checkDiskSpace());
            results.add(checkTempDirectory());
            results.add(checkUserHomePermissions());
        }
        
        // 输出检查结果
        int errorCount = 0;
        int warningCount = 0;
        int passCount = 0;
        
        for (CheckResult result : results) {
            String statusIcon;
            switch (result.status) {
                case PASS:
                    statusIcon = "✓";
                    passCount++;
                    break;
                case WARNING:
                    statusIcon = "⚠";
                    warningCount++;
                    break;
                case ERROR:
                    statusIcon = "✗";
                    errorCount++;
                    break;
                default:
                    statusIcon = "?";
            }
            
            output.append(String.format("[%s] %s\n", statusIcon, result.name));
            
            if (result.details != null && !result.details.isEmpty()) {
                for (String detail : result.details) {
                    output.append("    ").append(detail).append("\n");
                }
            }
            
            if (result.suggestion != null && (result.status != CheckStatus.PASS || verbose)) {
                output.append("    建议：").append(result.suggestion).append("\n");
            }
            output.append("\n");
        }
        
        // 输出汇总
        output.append("═══════════════════════════════════════════════════════════\n");
        output.append(String.format("检查结果：%d 通过，%d 警告，%d 错误\n", passCount, warningCount, errorCount));
        
        if (errorCount > 0) {
            output.append("\n发现错误！请根据建议修复问题。\n");
        } else if (warningCount > 0) {
            output.append("\n发现警告。建议检查并优化配置。\n");
        } else {
            output.append("\n所有检查通过！系统配置正常。\n");
        }
        
        return CommandResult.success(output.toString());
    }
    
    /**
     * 检查 Java 版本
     */
    private CheckResult checkJavaVersion() {
        CheckResult result = new CheckResult("Java 版本检查");
        
        try {
            String javaVersion = System.getProperty("java.version");
            String javaVendor = System.getProperty("java.vendor");
            String javaHome = System.getProperty("java.home");
            
            result.details.add("Java 版本：" + javaVersion);
            result.details.add("Java 厂商：" + javaVendor);
            result.details.add("Java 路径：" + javaHome);
            
            // 检查 Java 版本是否 >= 17
            String version = javaVersion.split("\\.")[0];
            int majorVersion = Integer.parseInt(version);
            
            if (majorVersion < 17) {
                result.status = CheckStatus.WARNING;
                result.suggestion = "建议升级到 Java 17 或更高版本以获得最佳性能。";
            } else {
                result.status = CheckStatus.PASS;
            }
        } catch (Exception e) {
            result.status = CheckStatus.ERROR;
            result.suggestion = "无法获取 Java 版本信息：" + e.getMessage();
        }
        
        return result;
    }
    
    /**
     * 检查内存配置
     */
    private CheckResult checkMemoryConfig() {
        CheckResult result = new CheckResult("内存配置检查");
        
        try {
            Runtime runtime = Runtime.getRuntime();
            
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            result.details.add("最大堆内存：" + formatBytes(maxMemory));
            result.details.add("已分配内存：" + formatBytes(totalMemory));
            result.details.add("已使用内存：" + formatBytes(usedMemory));
            result.details.add("可用内存：" + formatBytes(freeMemory));
            
            // 检查最大内存是否合理（至少 256MB）
            if (maxMemory < 256 * 1024 * 1024) {
                result.status = CheckStatus.WARNING;
                result.suggestion = "最大堆内存较小，建议通过 -Xmx 参数增加内存配置。";
            } else {
                result.status = CheckStatus.PASS;
            }
        } catch (Exception e) {
            result.status = CheckStatus.ERROR;
            result.suggestion = "无法获取内存配置：" + e.getMessage();
        }
        
        return result;
    }
    
    /**
     * 检查文件系统权限
     */
    private CheckResult checkFileSystemPermissions() {
        CheckResult result = new CheckResult("文件系统权限检查");
        
        try {
            String userDir = System.getProperty("user.dir");
            String userHome = System.getProperty("user.home");
            
            result.details.add("当前工作目录：" + userDir);
            result.details.add("用户主目录：" + userHome);
            
            // 检查工作目录权限
            java.nio.file.Path workPath = Paths.get(userDir);
            boolean readable = Files.isReadable(workPath);
            boolean writable = Files.isWritable(workPath);
            boolean executable = Files.isExecutable(workPath);
            
            result.details.add("工作目录可读：" + (readable ? "是" : "否"));
            result.details.add("工作目录可写：" + (writable ? "是" : "否"));
            result.details.add("工作目录可执行：" + (executable ? "是" : "否"));
            
            if (!readable || !writable) {
                result.status = CheckStatus.ERROR;
                result.suggestion = "工作目录权限不足，请检查目录权限设置。";
            } else {
                result.status = CheckStatus.PASS;
            }
        } catch (Exception e) {
            result.status = CheckStatus.ERROR;
            result.suggestion = "无法检查文件系统权限：" + e.getMessage();
        }
        
        return result;
    }
    
    /**
     * 检查配置文件
     */
    private CheckResult checkConfigFiles() {
        CheckResult result = new CheckResult("配置文件检查");
        
        try {
            String userHome = System.getProperty("user.home");
            java.nio.file.Path configDir = Paths.get(userHome, ".jwcode");
            
            if (Files.exists(configDir)) {
                result.details.add("配置目录存在：" + configDir);
                
                // 检查配置文件
                java.nio.file.Path configFile = configDir.resolve("config.json");
                if (Files.exists(configFile)) {
                    result.details.add("配置文件存在：" + configFile);
                    
                    // 尝试读取配置文件
                    String content = Files.readString(configFile);
                    if (content != null && !content.trim().isEmpty()) {
                        result.details.add("配置文件大小：" + content.length() + " 字节");
                        result.status = CheckStatus.PASS;
                    } else {
                        result.status = CheckStatus.WARNING;
                        result.suggestion = "配置文件为空。";
                    }
                } else {
                    result.details.add("配置文件不存在，将使用默认配置");
                    result.status = CheckStatus.PASS;
                }
            } else {
                result.details.add("配置目录不存在，将在首次使用时创建");
                result.status = CheckStatus.PASS;
            }
        } catch (Exception e) {
            result.status = CheckStatus.ERROR;
            result.suggestion = "无法检查配置文件：" + e.getMessage();
        }
        
        return result;
    }
    
    /**
     * 检查环境变量
     */
    private CheckResult checkEnvironmentVariables() {
        CheckResult result = new CheckResult("环境变量检查");
        
        try {
            Map<String, String> env = System.getenv();
            
            // 检查关键环境变量
            List<String> checkedVars = List.of("PATH", "JAVA_HOME", "USER_HOME");
            for (String var : checkedVars) {
                String value = env.get(var);
                if (value != null && !value.isEmpty()) {
                    result.details.add(var + ": 已设置");
                } else {
                    result.details.add(var + ": 未设置");
                }
            }
            
            // 检查 JWCode 相关变量
            String jwcodeHome = env.get("JWCODE_HOME");
            String jwcodeConfig = env.get("JWCODE_CONFIG");
            
            if (jwcodeHome != null) {
                result.details.add("JWCODE_HOME: " + jwcodeHome);
            }
            if (jwcodeConfig != null) {
                result.details.add("JWCODE_CONFIG: " + jwcodeConfig);
            }
            
            result.status = CheckStatus.PASS;
        } catch (Exception e) {
            result.status = CheckStatus.ERROR;
            result.suggestion = "无法检查环境变量：" + e.getMessage();
        }
        
        return result;
    }
    
    /**
     * 检查网络连接
     */
    private CheckResult checkNetworkConnectivity() {
        CheckResult result = new CheckResult("网络连接检查");
        
        try {
            // 检查是否可以访问外部网络
            java.net.InetAddress localhost = java.net.InetAddress.getLocalHost();
            result.details.add("主机名：" + localhost.getHostName());
            result.details.add("IP 地址：" + localhost.getHostAddress());
            
            // 尝试检查网络连通性
            boolean reachable;
            try {
                reachable = java.net.InetAddress.getByName("8.8.8.8").isReachable(2000);
            } catch (Exception e) {
                reachable = false;
            }
            
            if (reachable) {
                result.details.add("外部网络：可达");
                result.status = CheckStatus.PASS;
            } else {
                result.details.add("外部网络：不可达或超时");
                result.status = CheckStatus.WARNING;
                result.suggestion = "如果需要使用在线功能，请检查网络连接。";
            }
        } catch (Exception e) {
            result.status = CheckStatus.ERROR;
            result.suggestion = "无法检查网络连接：" + e.getMessage();
        }
        
        return result;
    }
    
    /**
     * 检查磁盘空间（verbose 模式）
     */
    private CheckResult checkDiskSpace() {
        CheckResult result = new CheckResult("磁盘空间检查");
        
        try {
            String userDir = System.getProperty("user.dir");
            java.nio.file.Path path = Paths.get(userDir);
            
            long totalSpace = path.toFile().getTotalSpace();
            long freeSpace = path.toFile().getFreeSpace();
            long usableSpace = path.toFile().getUsableSpace();
            
            result.details.add("总空间：" + formatBytes(totalSpace));
            result.details.add("空闲空间：" + formatBytes(freeSpace));
            result.details.add("可用空间：" + formatBytes(usableSpace));
            
            // 如果可用空间小于 1GB，发出警告
            if (usableSpace < 1024 * 1024 * 1024) {
                result.status = CheckStatus.WARNING;
                result.suggestion = "磁盘可用空间不足 1GB，建议清理磁盘空间。";
            } else {
                result.status = CheckStatus.PASS;
            }
        } catch (Exception e) {
            result.status = CheckStatus.ERROR;
            result.suggestion = "无法检查磁盘空间：" + e.getMessage();
        }
        
        return result;
    }
    
    /**
     * 检查临时目录（verbose 模式）
     */
    private CheckResult checkTempDirectory() {
        CheckResult result = new CheckResult("临时目录检查");
        
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            java.nio.file.Path tempPath = Paths.get(tempDir);
            
            result.details.add("临时目录：" + tempDir);
            result.details.add("目录存在：" + Files.exists(tempPath));
            result.details.add("可写：" + Files.isWritable(tempPath));
            
            if (Files.exists(tempPath) && Files.isWritable(tempPath)) {
                result.status = CheckStatus.PASS;
            } else {
                result.status = CheckStatus.WARNING;
                result.suggestion = "临时目录不可用，可能影响缓存功能。";
            }
        } catch (Exception e) {
            result.status = CheckStatus.ERROR;
            result.suggestion = "无法检查临时目录：" + e.getMessage();
        }
        
        return result;
    }
    
    /**
     * 检查用户主目录权限（verbose 模式）
     */
    private CheckResult checkUserHomePermissions() {
        CheckResult result = new CheckResult("用户主目录权限检查");
        
        try {
            String userHome = System.getProperty("user.home");
            java.nio.file.Path homePath = Paths.get(userHome);
            
            result.details.add("主目录：" + userHome);
            result.details.add("可读：" + Files.isReadable(homePath));
            result.details.add("可写：" + Files.isWritable(homePath));
            
            if (Files.isReadable(homePath) && Files.isWritable(homePath)) {
                result.status = CheckStatus.PASS;
            } else {
                result.status = CheckStatus.WARNING;
                result.suggestion = "主目录权限受限，可能影响配置保存。";
            }
        } catch (Exception e) {
            result.status = CheckStatus.ERROR;
            result.suggestion = "无法检查主目录权限：" + e.getMessage();
        }
        
        return result;
    }
    
    /**
     * 格式化字节大小
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    /**
     * 检查结果类
     */
    private static class CheckResult {
        String name;
        CheckStatus status = CheckStatus.PASS;
        List<String> details = new ArrayList<>();
        String suggestion;
        
        CheckResult(String name) {
            this.name = name;
        }
    }
    
    /**
     * 检查状态枚举
     */
    private enum CheckStatus {
        PASS,
        WARNING,
        ERROR
    }
}