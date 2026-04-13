package com.jwcode.core.repl;

import javax.script.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaScript REPL 执行器
 * 使用 Nashorn (JDK 8-14) 或 GraalVM JavaScript 引擎
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class JavaScriptREPLExecutor extends REPLExecutor {
    
    private static final Logger logger = Logger.getLogger(JavaScriptREPLExecutor.class.getName());
    
    private ScriptEngine scriptEngine;
    private Bindings bindings;
    private final Object engineLock = new Object();
    private final String engineName;
    
    /**
     * 默认构造函数
     * 超时 30 秒，内存限制 256MB
     */
    public JavaScriptREPLExecutor() {
        this(30000, 256);
    }
    
    /**
     * 创建 JavaScript REPL 执行器
     * 
     * @param timeoutMillis 超时时间（毫秒）
     * @param maxMemoryMB 最大内存限制（MB）
     */
    public JavaScriptREPLExecutor(long timeoutMillis, long maxMemoryMB) {
        super("javascript", timeoutMillis, maxMemoryMB);
        this.engineName = initializeEngine();
    }
    
    /**
     * 初始化脚本引擎
     */
    private String initializeEngine() {
        ScriptEngineManager manager = new ScriptEngineManager();
        
        // 尝试 GraalVM JavaScript
        scriptEngine = manager.getEngineByName("graal.js");
        if (scriptEngine != null) {
            logger.info("Using GraalVM JavaScript engine");
            setupGraalEngine();
            return "graal.js";
        }
        
        // 尝试 Nashorn
        scriptEngine = manager.getEngineByName("nashorn");
        if (scriptEngine != null) {
            logger.info("Using Nashorn JavaScript engine");
            setupNashornEngine();
            return "nashorn";
        }
        
        // 尝试通用的 JavaScript
        scriptEngine = manager.getEngineByName("JavaScript");
        if (scriptEngine != null) {
            logger.info("Using generic JavaScript engine: " + scriptEngine.getClass().getName());
            setupGenericEngine();
            return "javascript";
        }
        
        logger.warning("No JavaScript engine available");
        return null;
    }
    
    /**
     * 设置 GraalVM 引擎
     */
    private void setupGraalEngine() {
        bindings = scriptEngine.createBindings();
        // GraalVM 特定配置
        try {
            scriptEngine.getContext().setAttribute("polyglot.js.allowAllAccess", true, ScriptContext.ENGINE_SCOPE);
            scriptEngine.getContext().setAttribute("polyglot.js.allowHostAccess", true, ScriptContext.ENGINE_SCOPE);
            scriptEngine.getContext().setAttribute("polyglot.js.allowHostClassLookup", true, ScriptContext.ENGINE_SCOPE);
        } catch (Exception e) {
            logger.fine("Could not set GraalVM attributes: " + e.getMessage());
        }
        initializeBindings();
    }
    
    /**
     * 设置 Nashorn 引擎
     */
    private void setupNashornEngine() {
        bindings = scriptEngine.createBindings();
        // Nashorn 特定配置
        try {
            scriptEngine.getContext().setAttribute("nashorn.args", new String[]{"--language=es6"}, ScriptContext.ENGINE_SCOPE);
        } catch (Exception e) {
            logger.fine("Could not set Nashorn attributes: " + e.getMessage());
        }
        initializeBindings();
    }
    
    /**
     * 设置通用引擎
     */
    private void setupGenericEngine() {
        bindings = scriptEngine.createBindings();
        initializeBindings();
    }
    
    /**
     * 初始化绑定（全局变量和函数）
     */
    private void initializeBindings() {
        // 添加控制台输出函数
        bindings.put("console", new ConsoleObject());
        
        // 添加 print 函数
        bindings.put("print", new PrintFunction());
        bindings.put("println", new PrintlnFunction());
        
        // 添加实用工具
        bindings.put("JSON", new JSONUtil());
        
        scriptEngine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
    }
    
    @Override
    public ExecutionResult execute(String code) {
        if (!isAvailable()) {
            return ExecutionResult.error(
                "JavaScript engine is not available. Please use JDK with Nashorn or GraalVM.",
                "JS_ENGINE_UNAVAILABLE"
            );
        }
        
        if (code == null || code.trim().isEmpty()) {
            return ExecutionResult.success("", 0);
        }
        
        return executeWithTimeout(() -> {
            synchronized (engineLock) {
                OutputCapture capture = new OutputCapture();
                capture.start();
                
                try {
                    // 检查内存限制
                    if (isMemoryExceeded()) {
                        return ExecutionResult.memoryExceeded(maxMemoryMB);
                    }
                    
                    // 重置输出捕获器中的控制台
                    resetConsoleOutput();
                    
                    // 执行代码
                    Object result = scriptEngine.eval(code);
                    
                    // 获取输出
                    String output = capture.getStdout();
                    String error = capture.getStderr();
                    
                    // 如果有返回值且没有输出，显示返回值
                    if (output.isEmpty() && result != null && !code.trim().endsWith(";")) {
                        output = result.toString();
                    }
                    
                    if (!error.isEmpty()) {
                        return new ExecutionResult(false, output, error, 0, "ERROR");
                    }
                    
                    return ExecutionResult.success(output.trim(), 0);
                    
                } catch (ScriptException e) {
                    String errorMsg = "Syntax Error: " + e.getMessage();
                    if (e.getLineNumber() > 0) {
                        errorMsg += " (Line " + e.getLineNumber() + ")";
                    }
                    return ExecutionResult.error(errorMsg, "SYNTAX_ERROR");
                } catch (Exception e) {
                    return ExecutionResult.error("Runtime Error: " + e.getMessage(), "RUNTIME_ERROR");
                } finally {
                    capture.stop();
                }
            }
        });
    }
    
    /**
     * 重置控制台输出
     */
    private void resetConsoleOutput() {
        Object console = bindings.get("console");
        if (console instanceof ConsoleObject) {
            ((ConsoleObject) console).clear();
        }
    }
    
    @Override
    public void reset() {
        synchronized (engineLock) {
            initializeBindings();
        }
    }
    
    @Override
    public boolean isAvailable() {
        return scriptEngine != null && engineName != null;
    }
    
    /**
     * 获取引擎名称
     */
    public String getEngineName() {
        return engineName;
    }
    
    /**
     * 控制台对象
     */
    public static class ConsoleObject {
        private final StringBuilder output = new StringBuilder();
        
        public void log(Object... args) {
            for (Object arg : args) {
                output.append(arg).append(" ");
            }
            output.append("\n");
            System.out.println(output.toString().trim());
        }
        
        public void error(Object... args) {
            for (Object arg : args) {
                output.append(arg).append(" ");
            }
            output.append("\n");
            System.err.println(output.toString().trim());
        }
        
        public void warn(Object... args) {
            log(args);
        }
        
        public void info(Object... args) {
            log(args);
        }
        
        public void clear() {
            output.setLength(0);
        }
        
        public String getOutput() {
            return output.toString();
        }
    }
    
    /**
     * print 函数
     */
    public static class PrintFunction implements java.util.function.Consumer<Object> {
        @Override
        public void accept(Object obj) {
            System.out.print(obj);
        }
    }
    
    /**
     * println 函数
     */
    public static class PrintlnFunction implements java.util.function.Consumer<Object> {
        @Override
        public void accept(Object obj) {
            System.out.println(obj);
        }
    }
    
    /**
     * JSON 工具
     */
    public static class JSONUtil {
        public String stringify(Object obj) {
            if (obj == null) return "null";
            if (obj instanceof String) return "\"" + obj + "\"";
            if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
            return obj.toString();
        }
        
        public Object parse(String json) {
            // 简化实现，实际应该使用 JSON 解析库
            return json;
        }
    }
}
