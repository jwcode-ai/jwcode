package com.jwcode.core.repl;

import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.execution.LocalExecutionControlProvider;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Java REPL 执行器
 * 使用 JShell API (JDK 9+) 执行 Java 代码片段
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class JavaREPLExecutor extends REPLExecutor {
    
    private static final Logger logger = Logger.getLogger(JavaREPLExecutor.class.getName());
    private static final AtomicBoolean J_SHELL_AVAILABLE = new AtomicBoolean(false);
    
    static {
        try {
            Class.forName("jdk.jshell.JShell");
            J_SHELL_AVAILABLE.set(true);
        } catch (ClassNotFoundException e) {
            logger.warning("JShell not available (requires JDK 9+). Java REPL will be disabled.");
        }
    }
    
    private JShell jShell;
    private ByteArrayOutputStream outputStream;
    private PrintStream printStream;
    private final Object shellLock = new Object();
    
    /**
     * 默认构造函数
     * 超时 30 秒，内存限制 256MB
     */
    public JavaREPLExecutor() {
        this(30000, 256);
    }
    
    /**
     * 创建 Java REPL 执行器
     * 
     * @param timeoutMillis 超时时间（毫秒）
     * @param maxMemoryMB 最大内存限制（MB）
     */
    public JavaREPLExecutor(long timeoutMillis, long maxMemoryMB) {
        super("java", timeoutMillis, maxMemoryMB);
        initializeJShell();
    }
    
    /**
     * 初始化 JShell
     */
    private void initializeJShell() {
        if (!J_SHELL_AVAILABLE.get()) {
            return;
        }
        
        synchronized (shellLock) {
            try {
                outputStream = new ByteArrayOutputStream();
                printStream = new PrintStream(outputStream);
                
                jShell = JShell.builder()
                    .executionEngine(new LocalExecutionControlProvider(), null)
                    .out(printStream)
                    .err(printStream)
                    .build();
                
                // 添加常用导入
                addDefaultImports();
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to initialize JShell", e);
                jShell = null;
            }
        }
    }
    
    /**
     * 添加默认导入
     */
    private void addDefaultImports() {
        if (jShell == null) return;
        
        String[] defaultImports = {
            "java.util.*",
            "java.io.*",
            "java.nio.file.*",
            "java.nio.charset.*",
            "java.time.*",
            "java.math.*",
            "java.util.stream.*",
            "java.util.function.*",
            "java.text.*",
            "java.util.regex.*"
        };
        
        for (String importStmt : defaultImports) {
            try {
                jShell.eval("import " + importStmt + ";");
            } catch (Exception e) {
                logger.fine("Failed to import: " + importStmt);
            }
        }
    }
    
    @Override
    public ExecutionResult execute(String code) {
        if (!isAvailable()) {
            return ExecutionResult.error(
                "JShell is not available. Please use JDK 9 or higher.",
                "JSHELL_UNAVAILABLE"
            );
        }
        
        if (code == null || code.trim().isEmpty()) {
            return ExecutionResult.success("", 0);
        }
        
        return executeWithTimeout(() -> {
            synchronized (shellLock) {
                try {
                    // 清空输出缓冲区
                    outputStream.reset();
                    
                    // 检查内存限制
                    if (isMemoryExceeded()) {
                        return ExecutionResult.memoryExceeded(maxMemoryMB);
                    }
                    
                    // 执行代码
                    List<SnippetEvent> events = jShell.eval(code);
                    
                    // 获取输出
                    printStream.flush();
                    String output = outputStream.toString();
                    
                    // 处理执行事件
                    StringBuilder resultBuilder = new StringBuilder();
                    boolean hasError = false;
                    StringBuilder errorBuilder = new StringBuilder();
                    
                    for (SnippetEvent event : events) {
                        Snippet snippet = event.snippet();
                        
                        // 检查是否有异常
                        if (event.exception() != null) {
                            hasError = true;
                            errorBuilder.append("Exception: ")
                                       .append(event.exception().getMessage())
                                       .append("\n");
                        }
                        
                        // 处理不同类型的代码片段
                        switch (snippet.kind()) {
                            case EXPRESSION:
                                if (event.status() == Snippet.Status.VALID && event.value() != null) {
                                    resultBuilder.append(event.value()).append("\n");
                                }
                                break;
                            case VAR:
                                if (event.status() == Snippet.Status.VALID) {
                                    String varName = ((jdk.jshell.VarSnippet) snippet).name();
                                    String varValue = jShell.varValue((jdk.jshell.VarSnippet) snippet);
                                    resultBuilder.append(varName).append(" = ").append(varValue).append("\n");
                                }
                                break;
                            case METHOD:
                            case TYPE_DECL:
                            case IMPORT:
                                // 这些类型通常没有直接输出
                                break;
                            case STATEMENT:
                                // 语句可能有副作用输出，已经在 output 中捕获
                                break;
                            case ERRONEOUS:
                                hasError = true;
                                // 获取诊断信息
                                jShell.diagnostics(snippet).forEach(diag -> {
                                    errorBuilder.append("Error: ")
                                               .append(diag.getMessage(Locale.ENGLISH))
                                               .append("\n");
                                });
                                break;
                        }
                        
                        // 如果代码片段无效，收集错误信息
                        if (event.status() != Snippet.Status.VALID && snippet.kind() != Snippet.Kind.ERRONEOUS) {
                            jShell.diagnostics(snippet).forEach(diag -> {
                                errorBuilder.append(diag.getMessage(Locale.ENGLISH)).append("\n");
                            });
                        }
                    }
                    
                    // 组合输出
                    String finalOutput = output;
                    if (!resultBuilder.isEmpty()) {
                        finalOutput = output + (output.isEmpty() ? "" : "\n") + 
                                     resultBuilder.toString().trim();
                    }
                    
                    if (hasError) {
                        return ExecutionResult.error(
                            errorBuilder.toString().trim(),
                            "COMPILATION_ERROR"
                        );
                    }
                    
                    return ExecutionResult.success(finalOutput.trim(), 0);
                    
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "JShell execution error", e);
                    return ExecutionResult.error("Execution error: " + e.getMessage(), "RUNTIME_ERROR");
                }
            }
        });
    }
    
    @Override
    public void reset() {
        synchronized (shellLock) {
            if (jShell != null) {
                jShell.close();
            }
            initializeJShell();
        }
    }
    
    @Override
    public boolean isAvailable() {
        return J_SHELL_AVAILABLE.get() && jShell != null;
    }
    
    /**
     * 获取变量列表
     * 
     * @return 变量信息列表
     */
    public List<String> getVariables() {
        synchronized (shellLock) {
            if (jShell == null) {
                return List.of();
            }
            return jShell.variables()
                .map(var -> var.name() + " : " + var.typeName())
                .toList();
        }
    }
    
    /**
     * 获取方法列表
     * 
     * @return 方法信息列表
     */
    public List<String> getMethods() {
        synchronized (shellLock) {
            if (jShell == null) {
                return List.of();
            }
            return jShell.methods()
                .map(method -> method.name() + method.signature())
                .toList();
        }
    }
    
    /**
     * 获取导入列表
     * 
     * @return 导入语句列表
     */
    public List<String> getImports() {
        synchronized (shellLock) {
            if (jShell == null) {
                return List.of();
            }
            return jShell.imports()
                .map(imp -> "import " + imp.fullname() + ";")
                .toList();
        }
    }
    
    /**
     * 检查代码是否完整（用于多行输入）
     * 
     * @param code 代码
     * @return 完整性分析结果
     */
    public SourceCodeAnalysis.Completeness isComplete(String code) {
        synchronized (shellLock) {
            if (jShell == null) {
                return SourceCodeAnalysis.Completeness.EMPTY;
            }
            return jShell.sourceCodeAnalysis().analyzeCompletion(code).completeness();
        }
    }
    
    @Override
    public void shutdown() {
        synchronized (shellLock) {
            if (jShell != null) {
                jShell.close();
                jShell = null;
            }
            if (printStream != null) {
                printStream.close();
            }
        }
        super.shutdown();
    }
}
