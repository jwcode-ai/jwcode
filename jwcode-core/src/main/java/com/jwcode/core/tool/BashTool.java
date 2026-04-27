package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.tool.Tool;
import com.jwcode.core.tool.ToolProgress;
import com.jwcode.core.tool.ToolResult;
import com.jwcode.core.tool.ToolSchemaGenerator;
import com.jwcode.core.tool.ToolValidationResult;
import com.jwcode.core.tool.context.ToolExecutionContext;
import com.jwcode.core.tool.input.BashInput;
import com.jwcode.core.tool.output.BashOutput;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Bash 工具 - Shell 命令执行（重构后）
 * 
 * 对标 JavaScript 项目的 BashTool
 * 支持命令执行、超时控制、输出流处理、危险命令检测
 */
public class BashTool implements Tool<BashInput, BashOutput, BashTool.BashProgress> {
    
    private static final Logger logger = Logger.getLogger(BashTool.class.getName());
    
    // 配置常量
    private static final int DEFAULT_TIMEOUT_MS = 600000; // 10 分钟
    private static final int MAX_OUTPUT_LINES = 1000;
    private static final int MAX_OUTPUT_CHARS = 100000; // 100KB
    private static final int MAX_FILE_LIST_RESULTS = 100;
    
    // 危险命令模式
    private static final Set<String> DANGEROUS_COMMANDS = new HashSet<>(Arrays.asList(
        "rm -rf /", "rm -rf /*", "mkfs", "dd if=/dev/zero",
        ":(){:|:&};:", "chmod -R 777 /", "chown -R",
        "mv /* /dev/null", "> /dev/sda", "> /dev/mbr"
    ));
    
    private static final Set<String> DANGEROUS_PATTERNS = new HashSet<>(Arrays.asList(
        "curl.*\\|.*bash", "wget.*\\|.*bash",
        "sudo.*rm.*--no-preserve-root",
        "chmod.*777.*/", "chown.*root.*/"
    ));
    
    // 自动忽略的文件/目录模式（用于过滤文件列表输出）
    private static final Set<String> IGNORED_PATTERNS = new HashSet<>(Arrays.asList(
        "\\.git", "\\.gitignore", "\\.gitmodules",
        "\\\\target\\\\", "/target/", "\\\\target\\\\",
        "\\.class", "\\.jar", "\\.war",
        "\\.iml", "\\.ipr", "\\.iws",
        "node_modules", "\\.npm", "\\.yarn",
        "__pycache__", "\\.pyc", "\\.pyo",
        "\\.venv", "\\.env", "venv",
        "\\.gradle", "\\.idea",
        "\\.DS_Store", "Thumbs.db",
        "\\.log$", "\\.tmp$", "\\.temp$",
        "\\\\bin\\\\", "\\\\out\\\\", "/bin/", "/out/",
        "\\.bak$", "\\.swp$", "\\.swo$"
    ));
    
    @Override
    public String getName() {
        return "BashTool";
    }
    
    @Override
    public String getDescription() {
        return "执行 Bash/Shell 命令。支持命令输出捕获、超时控制、工作目录设置、环境变量配置等功能。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 BashTool 执行 Shell 命令。
               
               参数:
               - command: 要执行的命令（必需）
               - description: 命令描述（可选）
               - timeout: 超时时间（毫秒，可选，默认：600000）
               - cwd: 工作目录（可选）
               - env: 环境变量（可选）
               - require_approval: 是否需要用户确认（可选，默认：true）
               
               示例:
               - 简单命令：{"command": "ls -la"}
               - 带工作目录：{"command": "ls -la", "cwd": "/home/user"}
               - 带环境变量：{"command": "echo $PATH", "env": {"PATH": "/usr/bin:/bin"}}
               - 带超时：{"command": "sleep 10", "timeout": 5000}
               
               注意:
               - 危险命令需要用户确认
               - 输出会被截断以避免过大
               - 超时命令会被终止
               
               ========================================
               平台差异说明（重要！）
               ========================================
               
               1. Windows 系统：
                  - 默认使用 cmd.exe 执行命令
                  - 常用命令: dir, type, copy, del, move, mkdir, rd
                  - 注意: Unix 命令（ls, cat, grep, find 等）在 cmd.exe 中不存在！
                  
                  PowerShell 命令示例：
                  - 文件列表: Get-ChildItem -Path . -Recurse -Depth 3
                  - 搜索内容: Select-String -Pattern "keyword" -Recurse
                  - 查找文件: Get-ChildItem -Filter "*.java" -Recurse
                  - 读取文件: Get-Content -Path "file.txt"
                  - 切换编码: chcp 65001  (解决中文乱码问题)
                  
                  ⚠️ PowerShell 编码问题解决：
                  - 如果遇到 "'命令名' 无法识别" 错误，先执行: chcp 65001
                  - 或使用 PowerShell 7+ (pwsh)，它默认使用 UTF-8
               
               2. Unix/Linux 系统：
                  - 使用 /bin/bash 执行命令
                  - 常用命令: ls, cat, grep, find, chmod, chown 等
               
               ========================================
               智能推荐
               ========================================
               
               如果需要以下操作，建议使用专门的工具而不是 BashTool：
               
               📁 文件搜索/列表：
                  → 使用 GlobTool: {"pattern": "**/*.java"}
                  
               🔍 内容搜索：
                  → 使用 GrepTool: {"pattern": "search term", "path": "."}
                  
               📖 读取文件：
                  → 使用 FileReadTool: {"path": "file.txt"}
                  
               🔧 智能项目分析：
                  → 使用 SmartAnalyzeTool: {"query": "项目结构"}
               
               ========================================
               常见问题排查
               ========================================
               
               ❌ "'Get-ChildItem' 无法识别"
                  → 原因: PowerShell 编码问题
                  → 解决: 先执行 "chcp 65001" 切换编码
                  
               ❌ "'ls' 不是内部或外部命令"
                  → 原因: 在 Windows cmd.exe 中使用了 Unix 命令
                  → 解决: 改用 "dir" 或使用 GlobTool
                  
               ❌ "'grep' 不是内部或外部命令"
                  → 原因: 在 Windows 中使用了 Unix 命令
                  → 解决: 使用 PowerShell 的 Select-String 或 GrepTool
               
               ✅ 推荐流程：
                  1. 先考虑是否可以用专用工具（更快、更可靠）
                  2. 如果必须用 BashTool，注意平台差异
                  3. Windows 上优先使用 PowerShell 命令（更强大）
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        return ToolSchemaGenerator.generateSchema(BashInput.class);
    }
    
    @Override
    public JsonNode getOutputSchema() {
        return ToolSchemaGenerator.generateSchema(BashOutput.class);
    }
    
    @Override
    public TypeReference<BashInput> getInputType() {
        return new TypeReference<BashInput>() {};
    }
    
    @Override
    public TypeReference<BashOutput> getOutputType() {
        return new TypeReference<BashOutput>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<BashOutput>> call(
            BashInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<BashProgress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                ToolValidationResult validation = validate(input);
                if (!validation.isValid()) {
                    return ToolResult.error("输入验证失败: " + validation.getFormattedErrors());
                }
                
                // 检查权限
                if (!context.hasPermission("execute", input.command())) {
                    return ToolResult.error("没有权限执行命令: " + input.command());
                }
                
                // 检查危险命令
                if (input.isDangerousCommand()) {
                    if (input.requiresApproval()) {
                        return ToolResult.error("危险命令需要用户确认: " + input.command());
                    }
                    logger.warning("执行危险命令: " + input.command());
                }
                
                // 执行命令
                return executeCommand(input, context);
                
            } catch (Exception e) {
                logger.severe("执行命令失败: " + e.getMessage());
                return ToolResult.error("执行命令失败: " + e.getMessage());
            }
        });
    }
    
    @Override
    public ToolValidationResult validate(BashInput input) {
        ToolValidationResult.Builder builder = ToolValidationResult.builder();
        
        if (input.command() == null || input.command().trim().isEmpty()) {
            builder.addError("command 是必需的");
        } else {
            // 【新增】Windows 平台 Unix 命令检测
            if (isWindows()) {
                String warning = detectUnixCommandWarning(input.command());
                if (warning != null) {
                    builder.addWarning(warning);
                }
            }
            
            // 【新增】检测无法持久化的任务（会重试的任务）
            String persistentWarning = detectPersistentChangeWarning(input.command());
            if (persistentWarning != null) {
                builder.addWarning(persistentWarning);
            }
        }
        
        if (input.timeout() != null && input.timeout() < 100) {
            builder.addWarning("超时时间太短（" + input.timeout() + "ms），建议至少 1000ms");
        }
        
        if (input.timeout() != null && input.timeout() > 3600000) {
            builder.addWarning("超时时间太长（" + input.timeout() + "ms），建议不超过 3600000ms（1小时）");
        }
        
        return builder.build();
    }
    
    /**
     * 检测命令是否尝试持久化状态变更（这类任务无法完成，会导致 AI 不断重试）
     */
    private String detectPersistentChangeWarning(String command) {
        String lower = command.toLowerCase().trim();
        
        // 检测 cd 命令（切换工作目录）
        if (lower.startsWith("cd ") || lower.equals("cd") || 
            lower.startsWith("chdir ") || lower.equals("chdir") ||
            lower.startsWith("pushd ") || lower.equals("pushd")) {
            return "⚠️ 切换工作目录无法持久化！\n" +
                   "  - Session 的 workingDirectory 在会话创建时已固定，无法通过命令修改\n" +
                   "  - 每次命令执行都在独立进程中运行，目录变更不会保留\n" +
                   "  - 建议：在命令中使用完整路径，或在 BashTool 的 cwd 参数中指定工作目录";
        }
        
        // 检测 export/setx 命令（设置环境变量）
        if (lower.startsWith("export ") || lower.startsWith("setx ") ||
            lower.startsWith("set ") && lower.contains("=")) {
            return "⚠️ 设置环境变量无法持久化！\n" +
                   "  - 环境变量只在当前命令进程内有效\n" +
                   "  - 建议：使用 BashTool 的 env 参数传递环境变量";
        }
        
        // 检测 source 命令
        if (lower.startsWith("source ") || lower.startsWith(". ")) {
            return "⚠️ source 命令无法持久化环境变更！\n" +
                   "  - 脚本中的环境修改只在当前进程内有效\n" +
                   "  - 建议：直接在命令中使用完整路径和参数";
        }
        
        return null;
    }
    
    /**
     * 检测命令是否使用了 Windows 上不存在的 Unix 命令或不兼容语法
     */
    private String detectUnixCommandWarning(String command) {
        String lower = command.toLowerCase();
        String trimmed = command.trim();
        
        // 【修复】检测 PowerShell 不兼容的链式命令语法
        if (trimmed.contains(" && ") || trimmed.contains("&&")) {
            return "⚠️ PowerShell / cmd.exe 不支持 '&&' 链式命令！建议：\n" +
                   "  - 使用 ';' 分隔多个命令（顺序执行）\n" +
                   "  - 或使用 PowerShell: 'if ($?) { command2 }' 条件执行";
        }
        
        if (trimmed.contains(" || ") || (trimmed.contains("||") && !trimmed.contains("|"))) {
            return "⚠️ PowerShell / cmd.exe 不支持 '||' 链式命令！建议：\n" +
                   "  - 使用 PowerShell: 'if (-not $?) { command2 }' 条件执行";
        }
        
        // 【修复】检测反引号转义序列（JSON 传递后易解析失败）
        if (command.contains("`") && !command.contains("``")) {
            return "⚠️ 命令包含反引号，在 JSON → Shell 传递中可能解析失败！建议：\n" +
                   "  - 使用双引号字符串包裹含特殊字符的参数\n" +
                   "  - 避免在命令中使用反引号作为转义符";
        }
        
        // 【修复】检测裸字符串被当作命令（如 "Total:"）
        String firstToken = trimmed.split("\\s+")[0];
        if (!firstToken.isEmpty() && !firstToken.startsWith("\"") && !firstToken.startsWith("'") &&
            !firstToken.startsWith("$") && !firstToken.startsWith("@") &&
            (firstToken.endsWith(":") || firstToken.contains(":") && !firstToken.contains("\\") && !firstToken.contains("/"))) {
            return "⚠️ 命令片段 '" + firstToken + "' 可能被 PowerShell 解释为无效语法！建议：\n" +
                   "  - 使用引号包裹字符串参数: '\"Total: value\"'\n" +
                   "  - 或使用 Write-Output 输出文本";
        }
        
        // find 命令检测（最常见的问题）
        if (trimmed.startsWith("find ") || lower.contains(" find ") || 
            lower.contains(" -type ") || lower.contains(" -name ") || 
            lower.contains(" -path ") || lower.contains(" -regex ")) {
            return "⚠️ find 命令在 Windows cmd.exe 中不存在！建议：\n" +
                   "  1. 使用 GlobTool 工具搜索文件（跨平台）\n" +
                   "  2. 使用 PowerShell: Get-ChildItem -Recurse -Filter '*.java' -File\n" +
                   "  3. 使用 SmartAnalyzeTool 智能分析项目";
        }
        
        // 其他 Unix 命令检测
        if (trimmed.startsWith("grep ") || trimmed.startsWith("| grep")) {
            return "⚠️ grep 在 Windows cmd.exe 中不存在！建议使用 PowerShell: Select-String -Pattern 'keyword'";
        }
        
        if (trimmed.startsWith("tree ") || lower.contains(" tree ")) {
            return "⚠️ tree 命令在 Windows cmd.exe 中不存在！建议使用 PowerShell: Get-ChildItem -Recurse";
        }
        
        if (trimmed.startsWith("head ") || trimmed.contains(" | head")) {
            return "⚠️ head 在 Windows cmd.exe 中不存在！建议使用 PowerShell: Get-Content | Select-Object -First N";
        }
        
        if (trimmed.startsWith("tail ") || trimmed.contains(" | tail")) {
            return "⚠️ tail 在 Windows cmd.exe 中不存在！建议使用 PowerShell: Get-Content -Tail N";
        }
        
        if (trimmed.startsWith("cat ") || lower.startsWith("cat ")) {
            return "⚠️ cat 在 Windows cmd.exe 中不存在！建议使用 PowerShell: Get-Content 或 FileReadTool";
        }
        
        if (trimmed.startsWith("ls ") || trimmed.startsWith("ls$")) {
            return "⚠️ ls 在 Windows cmd.exe 中不存在！建议使用 PowerShell: Get-ChildItem 或 GlobTool";
        }
        
        if (trimmed.startsWith("rm ") || lower.startsWith("rm ")) {
            return "⚠️ rm 在 Windows cmd.exe 中不存在！建议使用 PowerShell: Remove-Item";
        }
        
        if (trimmed.startsWith("cp ") || lower.startsWith("cp ")) {
            return "⚠️ cp 在 Windows cmd.exe 中不存在！建议使用 PowerShell: Copy-Item";
        }
        
        if (trimmed.startsWith("mv ") || lower.startsWith("mv ")) {
            return "⚠️ mv 在 Windows cmd.exe 中不存在！建议使用 PowerShell: Move-Item";
        }
        
        if (trimmed.startsWith("chmod ") || lower.startsWith("chmod ")) {
            return "⚠️ chmod 在 Windows 上不存在！Windows 使用 icacls 命令管理权限";
        }
        
        if (trimmed.startsWith("pwd") || lower.startsWith("pwd ")) {
            return "⚠️ pwd 在 Windows cmd.exe 中不存在！建议使用 PowerShell: Get-Location 或 echo %cd%";
        }
        
        if (trimmed.startsWith("which ") || lower.startsWith("which ")) {
            return "⚠️ which 在 Windows cmd.exe 中不存在！建议使用 PowerShell: Get-Command";
        }
        
        if (trimmed.startsWith("touch ") || lower.startsWith("touch ")) {
            return "⚠️ touch 在 Windows cmd.exe 中不存在！建议使用 PowerShell: New-Item -ItemType File";
        }
        
        if (trimmed.startsWith("mkdir -p ") || lower.startsWith("mkdir -p ")) {
            return "⚠️ mkdir -p 在 Windows cmd.exe 中不存在！建议使用 PowerShell: New-Item -ItemType Directory -Force";
        }
        
        return null;
    }
    
    /**
     * 检测是否为 Windows 系统
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
    
    @Override
    public boolean isReadOnly(BashInput input) {
        // 检查是否是只读命令
        String command = input.command().toLowerCase();
        return command.startsWith("ls ") || command.startsWith("cat ") ||
               command.startsWith("echo ") || command.startsWith("pwd") ||
               command.startsWith("whoami") || command.startsWith("date");
    }
    
    @Override
    public boolean isConcurrencySafe(BashInput input) {
        // 大多数命令不是并发安全的
        return false;
    }
    
    @Override
    public boolean isDestructive(BashInput input) {
        return input.isDangerousCommand();
    }
    
    @Override
    public boolean requiresApproval(BashInput input) {
        return input.requiresApproval() && input.isDangerousCommand();
    }
    
    /**
     * 执行命令
     */
    private ToolResult<BashOutput> executeCommand(BashInput input, ToolExecutionContext context) {
        long startTime = System.currentTimeMillis();
        Process process = null;
        
        try {
            // 准备命令
            List<String> commandParts = prepareCommand(input.command());
            
            // 构建进程构建器
            ProcessBuilder processBuilder = new ProcessBuilder(commandParts);
            
            // 设置工作目录
            if (input.cwd() != null && !input.cwd().isEmpty()) {
                processBuilder.directory(Path.of(input.cwd()).toFile());
            } else if (context.getWorkingDirectory() != null) {
                processBuilder.directory(context.getWorkingDirectory().toFile());
            }
            
            // 设置环境变量
            if (input.env() != null && !input.env().isEmpty()) {
                Map<String, String> env = processBuilder.environment();
                env.putAll(input.env());
            }
            
            // 重定向错误流
            processBuilder.redirectErrorStream(true);
            
            // 启动进程
            process = processBuilder.start();
            
            // 读取输出
            StringBuilder outputBuilder = new StringBuilder();
            StringBuilder errorBuilder = new StringBuilder();
            
            // 使用线程读取输出
            CompletableFuture<Void> outputFuture = readProcessOutput(process, outputBuilder, errorBuilder);
            
            // 等待进程完成或超时
            long timeout = input.getTimeoutMillis();
            boolean completed = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            if (!completed) {
                // 超时，终止进程
                process.destroyForcibly();
                process.waitFor(5000, TimeUnit.MILLISECONDS); // 等待进程终止
                
                // 等待输出读取完成
                try {
                    outputFuture.get(2000, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    outputFuture.cancel(true);
                }
                
                String partialOutput = outputBuilder.toString();
                return ToolResult.success(BashOutput.timeout(
                    truncateOutput(partialOutput),
                    input.command(),
                    executionTime
                ));
            }
            
            // 等待输出读取完成
            outputFuture.get(2000, TimeUnit.MILLISECONDS);
            
            // 获取退出码
            int exitCode = process.exitValue();
            String stdout = outputBuilder.toString();
            String stderr = errorBuilder.toString();
            
            // 构建输出
            if (exitCode == 0) {
                // 对文件列举命令的输出进行过滤
                String filteredOutput = filterFileListOutput(stdout, input.command());
                BashOutput output = BashOutput.success(
                    truncateOutput(filteredOutput),
                    input.command(),
                    processBuilder.directory() != null ? processBuilder.directory().getPath() : null
                );
                return ToolResult.success(output);
            } else {
                String errorMsg = truncateOutput(stderr);
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = truncateOutput(stdout);
                }
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = "命令执行失败，无输出";
                }
                return ToolResult.error(errorMsg);
            }
            
        } catch (TimeoutException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return ToolResult.success(BashOutput.timeout("", input.command(), executionTime));
            
        } catch (Exception e) {
            logger.severe("命令执行异常: " + e.getMessage());
            return ToolResult.error("命令执行异常: " + e.getMessage());
            
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
    
    /**
     * 准备命令
     */
    private List<String> prepareCommand(String command) {
        // 根据操作系统选择 shell
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        
        // 【修复】自动转换 Unix 命令为 Windows 命令
        if (isWindows) {
            command = autoConvertCommand(command);
            logger.fine("Auto-converted command for Windows: " + truncateForLog(command));
        }
        
        // 【修复】自动检测 PowerShell 命令并切换执行器
        if (isWindows && isPowerShellCommand(command)) {
            logger.fine("Detected PowerShell command, using powershell.exe: " + truncateForLog(command));
            return Arrays.asList("powershell.exe", "-NoProfile", "-Command", command);
        }
        
        String shell = isWindows ? "cmd.exe" : "/bin/bash";
        String shellArg = isWindows ? "/c" : "-c";
        
        // 【修复】在 Windows 上切换到 UTF-8 编码，解决中文乱码问题
        if (isWindows) {
            command = "chcp 65001 >nul && " + command;
            logger.fine("Added chcp 65001 to command for UTF-8 encoding");
        }
        
        return Arrays.asList(shell, shellArg, command);
    }
    
    /**
     * 自动转换 Windows 不兼容的 Unix 命令为等价命令
     * 这是核心的跨平台兼容修复
     */
    private String autoConvertCommand(String command) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (!isWindows) {
            return command;
        }
        
        // 转换为小写进行匹配
        String lower = command.toLowerCase();
        
        // pwd -> cd (显示当前目录)
        if (lower.startsWith("pwd")) {
            logger.fine("Auto-converting pwd to cd for Windows");
            return "cd";
        }
        
        // ls -la -> dir (详细列表)
        if (lower.startsWith("ls ") || lower.startsWith("ls\t")) {
            logger.fine("Auto-converting ls to dir for Windows");
            return command.replaceFirst("(?i)ls(\\s+)", "dir $1");
        }
        
        // ll -> dir (简短列表)
        if (lower.startsWith("ll ")) {
            logger.fine("Auto-converting ll to dir for Windows");
            return command.replaceFirst("(?i)ll(\\s+)", "dir $1");
        }
        
        // cat -> type (显示文件内容)
        if (lower.startsWith("cat ")) {
            logger.fine("Auto-converting cat to type for Windows");
            return command.replaceFirst("(?i)cat(\\s+)", "type $1");
        }
        
        // rm -> del (删除文件)
        if (lower.startsWith("rm ")) {
            logger.fine("Auto-converting rm to del for Windows");
            return command.replaceFirst("(?i)rm(\\s+)", "del $1");
        }
        
        // cp -> copy (复制文件)
        if (lower.startsWith("cp ")) {
            logger.fine("Auto-converting cp to copy for Windows");
            return command.replaceFirst("(?i)cp(\\s+)", "copy $1");
        }
        
        // mv -> move (移动文件)
        if (lower.startsWith("mv ")) {
            logger.fine("Auto-converting mv to move for Windows");
            return command.replaceFirst("(?i)mv(\\s+)", "move $1");
        }
        
        // mkdir -> md (创建目录)
        if (lower.startsWith("mkdir ")) {
            logger.fine("Auto-converting mkdir to md for Windows");
            return command.replaceFirst("(?i)mkdir(\\s+)", "md $1");
        }
        
        // touch -> echo + type (创建空文件)
        if (lower.startsWith("touch ")) {
            logger.fine("Auto-converting touch to echo for Windows");
            // 提取文件名并创建空文件
            String filename = command.substring(6).trim();
            return "echo. > " + filename;
        }
        
        // which -> where (查找命令位置)
        if (lower.startsWith("which ")) {
            logger.fine("Auto-converting which to where for Windows");
            return command.replaceFirst("(?i)which(\\s+)", "where $1");
        }
        
        // clear -> cls (清屏)
        if (lower.equals("clear") || lower.startsWith("clear ")) {
            logger.fine("Auto-converting clear to cls for Windows");
            return "cls";
        }
        
        // grep -> findstr (搜索内容)
        if (lower.contains(" grep ")) {
            logger.fine("Auto-converting grep to findstr for Windows");
            return command.replaceAll("(?i)\\s+grep\\s+", " | findstr ");
        }
        
        return command;
    }
    
    /**
     * 检测命令是否为 PowerShell 命令
     * 包含 PowerShell cmdlet 时自动使用 powershell.exe 执行
     */
    private boolean isPowerShellCommand(String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }
        
        String lower = command.toLowerCase();
        
        // 常见 PowerShell cmdlet 检测
        String[] psCmdlets = {
            "get-childitem", "get-content", "get-item", "get-location",
            "select-string", "set-location", "test-path", "new-item",
            "remove-item", "copy-item", "move-item", "invoke-restmethod",
            "invoke-webrequest", "write-output", "write-host"
        };
        
        for (String cmdlet : psCmdlets) {
            if (lower.contains(cmdlet)) {
                return true;
            }
        }
        
        // PowerShell 特有语法检测
        if (lower.contains("-path ") || lower.contains("-filter ") ||
            lower.contains("-recurse") || lower.contains("-first ") ||
            lower.contains("-last ") || lower.contains("-pattern ") ||
            lower.contains("| where-object") || lower.contains("| select-object") ||
            lower.contains("$env:") || lower.contains("${")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 日志输出时截断命令（避免敏感信息泄露）
     */
    private String truncateForLog(String command) {
        if (command == null) return "null";
        return command.length() > 50 ? command.substring(0, 50) + "..." : command;
    }
    
    /**
     * 读取进程输出
     */
    private CompletableFuture<Void> readProcessOutput(
            Process process, 
            StringBuilder stdoutBuilder, 
            StringBuilder stderrBuilder) {
        
        return CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                
                String line;
                int lineCount = 0;
                
                while ((line = reader.readLine()) != null) {
                    if (lineCount < MAX_OUTPUT_LINES) {
                        stdoutBuilder.append(line).append("\n");
                        lineCount++;
                    } else {
                        // 超过最大行数，停止读取
                        break;
                    }
                    
                    // 检查总字符数
                    if (stdoutBuilder.length() > MAX_OUTPUT_CHARS) {
                        stdoutBuilder.setLength(MAX_OUTPUT_CHARS);
                        stdoutBuilder.append("\n...[输出被截断]");
                        break;
                    }
                }
                
            } catch (IOException e) {
                logger.warning("读取进程输出失败: " + e.getMessage());
            }
            
            // 读取错误流
            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                
                String line;
                while ((line = errorReader.readLine()) != null) {
                    stderrBuilder.append(line).append("\n");
                    
                    // 检查总字符数
                    if (stderrBuilder.length() > MAX_OUTPUT_CHARS) {
                        stderrBuilder.setLength(MAX_OUTPUT_CHARS);
                        stderrBuilder.append("\n...[错误输出被截断]");
                        break;
                    }
                }
                
            } catch (IOException e) {
                logger.warning("读取进程错误输出失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 截断输出
     */
    private String truncateOutput(String output) {
        if (output.length() <= MAX_OUTPUT_CHARS) {
            return output;
        }
        
        return output.substring(0, MAX_OUTPUT_CHARS) + "\n...[输出被截断，总长度: " + output.length() + " 字符]";
    }
    
    /**
     * 过滤文件列表输出，自动忽略无关文件
     * 用于 dir、ls 等列举文件的命令
     */
    private String filterFileListOutput(String output, String command) {
        // 检测是否是文件列举命令
        String lowerCommand = command.toLowerCase();
        boolean isFileListCommand = lowerCommand.contains("dir ") || 
                                   lowerCommand.contains("ls ") ||
                                   lowerCommand.contains("find ") ||
                                   lowerCommand.contains("get-childitem");
        
        if (!isFileListCommand) {
            return output;
        }
        
        String[] lines = output.split("\n");
        List<String> resultLines = new ArrayList<>();
        int ignoredCount = 0;
        
        for (String line : lines) {
            if (shouldIgnoreLine(line)) {
                ignoredCount++;
                continue;
            }
            resultLines.add(line);
        }
        
        StringBuilder filtered = new StringBuilder();
        boolean truncated = false;
        int totalResults = resultLines.size();
        
        if (totalResults > MAX_FILE_LIST_RESULTS) {
            for (int i = 0; i < MAX_FILE_LIST_RESULTS; i++) {
                filtered.append(resultLines.get(i)).append("\n");
            }
            filtered.append("\n⚠️ 文件过多（共 ").append(totalResults).append(" 个，超过 ").append(MAX_FILE_LIST_RESULTS).append(" 个限制）。已只显示前 ").append(MAX_FILE_LIST_RESULTS).append(" 个。\n");
            filtered.append("💡 建议：请按子目录逐个扫描，或使用更精确的过滤条件（如 -Filter '*.java'、-Depth 2、-Exclude node_modules）。\n");
            truncated = true;
        } else {
            for (String line : resultLines) {
                filtered.append(line).append("\n");
            }
        }
        
        String result = filtered.toString();
        if (ignoredCount > 0) {
            if (truncated) {
                result = result.trim() + "\n[此外已自动过滤 " + ignoredCount + " 个无关文件/目录，如 .git、target、node_modules 等]";
            } else {
                result = result.trim() + "\n\n[已自动过滤 " + ignoredCount + " 个无关文件/目录，如 .git、target、.class 等]";
            }
        }
        
        return result;
    }
    
    /**
     * 检查行是否应该被忽略
     */
    private boolean shouldIgnoreLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        
        String normalizedLine = line.replace('\\', '/');
        
        for (String pattern : IGNORED_PATTERNS) {
            try {
                // 处理 Windows 路径模式
                String regex = pattern;
                if (pattern.contains("\\\\")) {
                    regex = pattern.replace("\\\\", "/");
                }
                
                if (normalizedLine.matches(".*" + regex + ".*")) {
                    return true;
                }
            } catch (Exception e) {
                // 正则表达式无效，跳过此模式
            }
        }
        
        return false;
    }
    
    /**
     * Bash 执行进度
     */
    public static class BashProgress {
        private final String command;
        private final long elapsedTime;
        private final boolean running;
        private final int outputLines;
        
        public BashProgress(String command, long elapsedTime, boolean running, int outputLines) {
            this.command = command;
            this.elapsedTime = elapsedTime;
            this.running = running;
            this.outputLines = outputLines;
        }
        
        public String getCommand() { return command; }
        public long getElapsedTime() { return elapsedTime; }
        public boolean isRunning() { return running; }
        public int getOutputLines() { return outputLines; }
    }
    
    /**
     * 输入类型（Tool 接口需要）
     */
    public static class Input {
        public String command;
        public String description;
        public Integer timeout;
        public String cwd;
        public java.util.Map<String, String> env;
        public Boolean requireApproval;
        
        public Input() {}
        
        public Input(String command) {
            this.command = command;
        }
    }
    
    /**
     * 输出类型（Tool 接口需要）
     */
    public static class Output {
        public String stdout;
        public String stderr;
        public Integer exitCode;
        public String command;
        public String cwd;
        
        public Output() {}
    }
    
    /**
     * 进度类型（Tool 接口需要）
     */
    public static class Progress {
        private final String command;
        private final long elapsedTime;
        private final boolean running;
        private final int outputLines;
        
        public Progress(String command, long elapsedTime, boolean running, int outputLines) {
            this.command = command;
            this.elapsedTime = elapsedTime;
            this.running = running;
            this.outputLines = outputLines;
        }
        
        public String getCommand() { return command; }
        public long getElapsedTime() { return elapsedTime; }
        public boolean isRunning() { return running; }
        public int getOutputLines() { return outputLines; }
    }
}
