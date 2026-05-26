package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.repl.REPLExecutor;
import com.jwcode.core.repl.REPLFactory;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * REPL 工具 - 交互式编程环境
 * 使用 REPLFactory 执行代码，支持 Java, Python, JavaScript
 * 包含代码安全沙箱和超时处理
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class REPLTool implements Tool<REPLTool.Input, REPLTool.Output, REPLTool.Progress> {
    
    private static final Logger logger = Logger.getLogger(REPLTool.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    // 危险代码模式（安全检查）
    private static final Set<Pattern> DANGEROUS_PATTERNS = new HashSet<>();
    private static final Set<String> DANGEROUS_IMPORTS = new HashSet<>();
    
    static {
        // 文件系统操作
        DANGEROUS_PATTERNS.add(Pattern.compile("(?i)\\b(file|path|files)\\s*\\.\\s*(delete|deleteIfExists|move|copy)"));
        DANGEROUS_PATTERNS.add(Pattern.compile("(?i)\\brm\\s+-rf"));
        DANGEROUS_PATTERNS.add(Pattern.compile("(?i)\\bshutil\\.rmtree"));
        DANGEROUS_PATTERNS.add(Pattern.compile("(?i)\\bos\\.remove|os\\.rmdir|os\\.unlink"));
        
        // 系统命令执行
        DANGEROUS_PATTERNS.add(Pattern.compile("(?i)\\bruntime\\.getRuntime\\(\\)\\.exec"));
        DANGEROUS_PATTERNS.add(Pattern.compile("(?i)\\bprocessbuilder"));
        DANGEROUS_PATTERNS.add(Pattern.compile("(?i)\\bsubprocess\\.call|subprocess\\.run|subprocess\\.Popen"));
        DANGEROUS_PATTERNS.add(Pattern.compile("(?i)\\bos\\.system|os\\.popen"));
        
        // 网络操作
        DANGEROUS_PATTERNS.add(Pattern.compile("(?i)\\bsocket\\.connect"));
        DANGEROUS_PATTERNS.add(Pattern.compile("(?i)\\burllib\\.request|http\\.client"));
        
        // 反射和类加载
        DANGEROUS_PATTERNS.add(Pattern.compile("(?i)\\bclass\\.forName"));
        DANGEROUS_PATTERNS.add(Pattern.compile("(?i)\\bdefineClass|loadClass"));
        DANGEROUS_PATTERNS.add(Pattern.compile("(?i)\\bmethod\\.invoke"));
        
        // 危险的导入
        DANGEROUS_IMPORTS.addAll(Arrays.asList(
            "java.lang.reflect",
            "sun.misc",
            "java.rmi",
            "java.net.Socket",
            "java.net.ServerSocket"
        ));
    }
    
    private final REPLFactory replFactory;
    
    public REPLTool() {
        this(REPLFactory.getInstance());
    }
    
    public REPLTool(REPLFactory replFactory) {
        this.replFactory = replFactory;
    }
    
    @Override
    public String getName() {
        return "REPL";
    }
    
    @Override
    public String getDescription() {
        return "交互式编程环境（Read-Eval-Print Loop）。用于执行 Java、Python、JavaScript 代码片段。";
    }
    
    @Override
    public String getPrompt() {
        return """
               使用 REPL 工具执行代码片段。
               
               参数:
               - code: 要执行的代码（必需）
               - language: 编程语言（可选，支持 "java", "python", "javascript"，默认 "python"）
               - timeout: 超时时间（可选，默认 30 秒）
               - reset: 是否重置执行环境（可选，默认 false）
               
               示例:
               - {"code": "print('Hello World')", "language": "python"}
               - {"code": "System.out.println(2 + 2);", "language": "java"}
               - {"code": "console.log('Hello');", "language": "javascript"}
               - {"code": "x = 10\\ny = 20\\nprint(x + y)", "language": "python"}
               
               注意:
               - 代码在安全沙箱中执行，某些危险操作会被阻止
               - 执行有超时限制，避免无限循环
               - 支持多行代码（使用 \\n 分隔）
               - 变量状态会在多次调用间保持（除非 reset=true）
               """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "code": {
                            "type": "string",
                            "description": "要执行的代码"
                        },
                        "language": {
                            "type": "string",
                            "description": "编程语言",
                            "enum": ["java", "python", "javascript"],
                            "default": "python"
                        },
                        "timeout": {
                            "type": "integer",
                            "description": "超时时间（秒）",
                            "default": 30,
                            "minimum": 1,
                            "maximum": 300
                        },
                        "reset": {
                            "type": "boolean",
                            "description": "是否重置执行环境",
                            "default": false
                        }
                    },
                    "required": ["code"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public TypeReference<Input> getInputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public TypeReference<Output> getOutputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 验证输入
                ToolValidationResult validation = validate(input);
                if (!validation.isValid()) {
                    return ToolResult.error(validation.getErrors().get(0));
                }
                
                // 安全检查
                SecurityCheckResult securityCheck = checkCodeSecurity(input.code, input.language);
                if (!securityCheck.isSafe()) {
                    logger.warning("Security check failed: " + securityCheck.getReason());
                    Output output = new Output();
                    output.success = false;
                    output.result = "";
                    output.error = "Security check failed: " + securityCheck.getReason();
                    output.exitCode = "SECURITY_VIOLATION";
                    return ToolResult.success(output);
                }
                
                String language = input.language != null ? input.language.toLowerCase() : "python";
                int timeoutSeconds = input.timeout != null ? input.timeout : 30;
                
                // 如果需要重置环境
                if (Boolean.TRUE.equals(input.reset)) {
                    replFactory.resetExecutor(language);
                }
                
                // 获取 REPL 执行器
                REPLExecutor executor = replFactory.getExecutor(language, timeoutSeconds * 1000L, 256);
                
                if (executor == null) {
                    Output output = new Output();
                    output.success = false;
                    output.error = "Unsupported language or REPL not available: " + language
                        + ". Java REPL requires JDK 9+ (JShell), JavaScript REPL requires Nashorn/GraalVM JS engine.";
                    output.exitCode = "UNSUPPORTED_LANGUAGE";
                    return ToolResult.success(output);
                }
                
                if (!executor.isAvailable()) {
                    Output output = new Output();
                    output.success = false;
                    output.error = "REPL executor for '" + language + "' is not available. "
                        + "Java REPL requires JDK 9+ (JShell), JavaScript REPL requires Nashorn/GraalVM JS engine.";
                    output.exitCode = "EXECUTOR_UNAVAILABLE";
                    return ToolResult.success(output);
                }
                
                // 执行代码
                logger.info("Executing " + language + " code (timeout: " + timeoutSeconds + "s)");
                REPLExecutor.ExecutionResult result = executor.execute(input.code);
                
                // 构建输出
                Output output = new Output();
                output.success = result.success();
                output.result = result.output();
                output.error = result.error();
                output.executionTime = result.executionTimeMs();
                output.exitCode = result.exitCode();
                output.language = language;
                
                if (result.success()) {
                    logger.info("Code executed successfully in " + result.executionTimeMs() + "ms");
                } else {
                    logger.warning("Code execution failed: " + result.error());
                }
                
                return ToolResult.success(output);
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "REPL execution error", e);
                return ToolResult.error("REPL execution error: " + e.getMessage());
            }
        });
    }
    
    /**
     * 代码安全检查
     */
    private SecurityCheckResult checkCodeSecurity(String code, String language) {
        if (code == null || code.trim().isEmpty()) {
            return SecurityCheckResult.safe();
        }
        
        // 检查危险模式
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(code).find()) {
                return SecurityCheckResult.unsafe(
                    "Code contains potentially dangerous operations: " + pattern.pattern());
            }
        }
        
        // 检查危险导入
        for (String dangerousImport : DANGEROUS_IMPORTS) {
            if (code.contains("import " + dangerousImport) || 
                code.contains("from " + dangerousImport)) {
                return SecurityCheckResult.unsafe(
                    "Code contains restricted import: " + dangerousImport);
            }
        }
        
        // 语言特定的检查
        if ("python".equalsIgnoreCase(language)) {
            // Python 特定检查
            if (code.contains("__import__") || code.contains("exec(") || code.contains("eval(")) {
                return SecurityCheckResult.unsafe(
                    "Code contains restricted Python operations");
            }
        }
        
        return SecurityCheckResult.safe();
    }
    
    @Override
    public ToolValidationResult validate(Input input) {
        ToolValidationResult.Builder builder = ToolValidationResult.builder();
        
        if (input == null) {
            return builder.addError("输入不能为空").build();
        }
        
        if (input.code == null || input.code.trim().isEmpty()) {
            builder.addError("code 是必需的");
        }
        
        if (input.language != null) {
            Set<String> supported = Set.of("java", "python", "javascript", "js");
            if (!supported.contains(input.language.toLowerCase())) {
                builder.addError("不支持的语言: " + input.language);
            }
        }
        
        if (input.timeout != null && (input.timeout < 1 || input.timeout > 300)) {
            builder.addError("timeout 必须在 1-300 秒之间");
        }
        
        return builder.build();
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        return true; // REPL 执行是只读操作（除了内存/CPU使用）
    }
    
    @Override
    public boolean isConcurrencySafe(Input input) {
        return true;
    }
    
    /**
     * 输入类型
     */
    public static class Input {
        public String code;
        public String language;
        public Integer timeout;
        public Boolean reset;
        
        public Input() {}
        
        public Input(String code, String language) {
            this.code = code;
            this.language = language;
        }
    }
    
    /**
     * 输出类型
     */
    public static class Output {
        public boolean success;
        public String result;
        public String error;
        public Long executionTime;
        public String exitCode;
        public String language;
        
        public Output() {}
    }
    
    /**
     * 进度类型
     */
    public static class Progress {
        private final String status;
        private final int percent;
        
        public Progress(String status, int percent) {
            this.status = status;
            this.percent = percent;
        }
        
        public String getStatus() { return status; }
        public int getPercent() { return percent; }
    }
    
    /**
     * 安全检查结果
     */
    private static class SecurityCheckResult {
        private final boolean safe;
        private final String reason;
        
        private SecurityCheckResult(boolean safe, String reason) {
            this.safe = safe;
            this.reason = reason;
        }
        
        static SecurityCheckResult safe() {
            return new SecurityCheckResult(true, null);
        }
        
        static SecurityCheckResult unsafe(String reason) {
            return new SecurityCheckResult(false, reason);
        }
        
        boolean isSafe() {
            return safe;
        }
        
        String getReason() {
            return reason;
        }
    }
}
