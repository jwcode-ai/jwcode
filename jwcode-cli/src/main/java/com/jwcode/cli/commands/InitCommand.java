package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * InitCommand - /init 命令
 * 
 * 功能说明：
 * 初始化 JWCode 项目，创建配置文件、初始化工作区。
 * 
 * 初始化内容：
 * - 创建 .jwcode 配置目录
 * - 生成默认配置文件 jwcode.config.json
 * - 初始化会话存储目录
 * - 创建必要的模板文件
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/init", description = "项目初始化", 
         aliases = {"/initialize", "/setup"})
public class InitCommand implements Runnable {
    
    @Parameters(index = "0", description = "初始化模式：default, full, minimal", 
                defaultValue = "default")
    private String mode;
    
    @Option(names = {"-f", "--force"}, description = "强制初始化，覆盖现有配置")
    private boolean force;
    
    @Option(names = {"-q", "--quiet"}, description = "静默模式，减少输出")
    private boolean quiet;
    
    @Option(names = {"-t", "--template"}, description = "使用模板：java, python, javascript, go")
    private String template;
    
    @Option(names = {"-d", "--directory"}, description = "指定配置目录", defaultValue = ".jwcode")
    private String directory;
    
    @Override
    public void run() {
        if (!quiet) {
            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║         JWCode 项目初始化              ║");
            System.out.println("╚════════════════════════════════════════╝");
            System.out.println();
        }
        
        Path configDir = Paths.get(directory);
        
        // 检查是否已存在配置
        if (Files.exists(configDir) && !force) {
            System.out.println("⚠ 配置目录已存在：" + configDir.toAbsolutePath());
            System.out.println();
            System.out.println("选项:");
            System.out.println("  1. 使用 --force 强制覆盖现有配置");
            System.out.println("  2. 删除现有配置目录后重新初始化");
            System.out.println("  3. 跳过初始化");
            return;
        }
        
        // 创建配置目录
        try {
            if (!quiet) {
                System.out.println("正在创建配置目录...");
            }
            Files.createDirectories(configDir);
            if (!quiet) {
                System.out.println("✓ 配置目录已创建：" + configDir.toAbsolutePath());
            }
        } catch (IOException e) {
            System.out.println("❌ 无法创建配置目录：" + e.getMessage());
            return;
        }
        
        // 根据模式执行不同的初始化
        switch (mode.toLowerCase()) {
            case "default":
                initDefault(configDir);
                break;
            case "full":
                initFull(configDir);
                break;
            case "minimal":
                initMinimal(configDir);
                break;
            default:
                System.out.println("未知模式：" + mode);
                System.out.println("可用模式：default, full, minimal");
                return;
        }
        
        if (!quiet) {
            System.out.println();
            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║           初始化完成！                 ║");
            System.out.println("╚════════════════════════════════════════╝");
            System.out.println();
            System.out.println("下一步:");
            System.out.println("  1. 使用 /login 登录您的账户");
            System.out.println("  2. 使用 /config 编辑配置");
            System.out.println("  3. 开始使用 JWCode！");
        }
    }
    
    /**
     * 默认初始化
     */
    private void initDefault(Path configDir) {
        if (!quiet) {
            System.out.println();
            System.out.println("执行默认初始化...");
        }
        
        // 创建主配置文件
        createConfigFile(configDir.resolve("config.json"));
        
        // 创建会话目录
        createSessionsDirectory(configDir);
        
        // 创建日志目录
        createLogsDirectory(configDir);
        
        if (!quiet) {
            System.out.println("✓ 默认初始化完成");
        }
    }
    
    /**
     * 完整初始化
     */
    private void initFull(Path configDir) {
        if (!quiet) {
            System.out.println();
            System.out.println("执行完整初始化...");
        }
        
        // 执行默认初始化
        initDefault(configDir);
        
        // 创建 MCP 配置
        createMcpConfig(configDir.resolve("mcp.json"));
        
        // 创建插件目录
        createPluginsDirectory(configDir);
        
        // 创建模板目录
        createTemplatesDirectory(configDir);
        
        // 创建快捷键配置
        createKeybindingsConfig(configDir.resolve("keybindings.json"));
        
        // 如果指定了模板，创建项目模板
        if (template != null) {
            createProjectTemplate(configDir, template);
        }
        
        if (!quiet) {
            System.out.println("✓ 完整初始化完成");
        }
    }
    
    /**
     * 最小化初始化
     */
    private void initMinimal(Path configDir) {
        if (!quiet) {
            System.out.println();
            System.out.println("执行最小化初始化...");
        }
        
        // 仅创建主配置文件
        createConfigFile(configDir.resolve("config.json"));
        
        if (!quiet) {
            System.out.println("✓ 最小化初始化完成");
        }
    }
    
    /**
     * 创建主配置文件
     */
    private void createConfigFile(Path path) {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("version", "1.0.0");
            config.put("theme", "dark");
            config.put("language", "zh-CN");
            config.put("autoSave", true);
            config.put("maxHistoryLength", 1000);
            
            String content = toJsonString(config);
            Files.writeString(path, content);
            if (!quiet) {
                System.out.println("  ✓ 创建配置文件：" + path.getFileName());
            }
        } catch (IOException e) {
            System.out.println("  ❌ 无法创建配置文件：" + e.getMessage());
        }
    }
    
    /**
     * 创建会话目录
     */
    private void createSessionsDirectory(Path configDir) {
        Path sessionsDir = configDir.resolve("sessions");
        try {
            Files.createDirectories(sessionsDir);
            if (!quiet) {
                System.out.println("  ✓ 创建会话目录：sessions/");
            }
        } catch (IOException e) {
            System.out.println("  ❌ 无法创建会话目录：" + e.getMessage());
        }
    }
    
    /**
     * 创建日志目录
     */
    private void createLogsDirectory(Path configDir) {
        Path logsDir = configDir.resolve("logs");
        try {
            Files.createDirectories(logsDir);
            if (!quiet) {
                System.out.println("  ✓ 创建日志目录：logs/");
            }
        } catch (IOException e) {
            System.out.println("  ❌ 无法创建日志目录：" + e.getMessage());
        }
    }
    
    /**
     * 创建 MCP 配置
     */
    private void createMcpConfig(Path path) {
        try {
            Map<String, Object> mcpConfig = new HashMap<>();
            mcpConfig.put("enabled", true);
            mcpConfig.put("servers", new HashMap<>());
            
            String content = toJsonString(mcpConfig);
            Files.writeString(path, content);
            if (!quiet) {
                System.out.println("  ✓ 创建 MCP 配置：" + path.getFileName());
            }
        } catch (IOException e) {
            System.out.println("  ❌ 无法创建 MCP 配置：" + e.getMessage());
        }
    }
    
    /**
     * 创建插件目录
     */
    private void createPluginsDirectory(Path configDir) {
        Path pluginsDir = configDir.resolve("plugins");
        try {
            Files.createDirectories(pluginsDir);
            if (!quiet) {
                System.out.println("  ✓ 创建插件目录：plugins/");
            }
        } catch (IOException e) {
            System.out.println("  ❌ 无法创建插件目录：" + e.getMessage());
        }
    }
    
    /**
     * 创建模板目录
     */
    private void createTemplatesDirectory(Path configDir) {
        Path templatesDir = configDir.resolve("templates");
        try {
            Files.createDirectories(templatesDir);
            if (!quiet) {
                System.out.println("  ✓ 创建模板目录：templates/");
            }
        } catch (IOException e) {
            System.out.println("  ❌ 无法创建模板目录：" + e.getMessage());
        }
    }
    
    /**
     * 创建快捷键配置
     */
    private void createKeybindingsConfig(Path path) {
        try {
            Map<String, Object> keybindings = new HashMap<>();
            keybindings.put("acceptSuggestion", "Tab");
            keybindings.put("nextSuggestion", "ArrowDown");
            keybindings.put("prevSuggestion", "ArrowUp");
            keybindings.put("clearInput", "Ctrl+L");
            keybindings.put("exit", "Ctrl+D");
            
            String content = toJsonString(keybindings);
            Files.writeString(path, content);
            if (!quiet) {
                System.out.println("  ✓ 创建快捷键配置：" + path.getFileName());
            }
        } catch (IOException e) {
            System.out.println("  ❌ 无法创建快捷键配置：" + e.getMessage());
        }
    }
    
    /**
     * 创建项目模板
     */
    private void createProjectTemplate(Path configDir, String template) {
        Path templateDir = configDir.resolve("templates").resolve(template);
        try {
            Files.createDirectories(templateDir);
            
            // 创建 README 模板
            Path readmePath = templateDir.resolve("README.md");
            String readmeContent = "# " + capitalize(template) + " Project\n\n" +
                    "这是一个 " + template + " 项目模板。\n\n" +
                    "## 快速开始\n\n" +
                    "1. 克隆项目\n" +
                    "2. 安装依赖\n" +
                    "3. 开始编码\n";
            Files.writeString(readmePath, readmeContent);
            
            if (!quiet) {
                System.out.println("  ✓ 创建项目模板：" + template);
            }
        } catch (IOException e) {
            System.out.println("  ❌ 无法创建项目模板：" + e.getMessage());
        }
    }
    
    /**
     * 将 Map 转换为简单的 JSON 字符串
     */
    private String toJsonString(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{\n");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("  \"").append(entry.getKey()).append("\": ");
            if (entry.getValue() instanceof String) {
                sb.append("\"").append(entry.getValue()).append("\"");
            } else if (entry.getValue() instanceof Boolean) {
                sb.append(entry.getValue());
            } else if (entry.getValue() instanceof Map) {
                sb.append(toJsonString((Map<String, Object>) entry.getValue()));
            } else {
                sb.append(entry.getValue());
            }
        }
        sb.append("\n}");
        return sb.toString();
    }
    
    /**
     * 首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}