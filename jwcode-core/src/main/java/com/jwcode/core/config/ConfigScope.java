package com.jwcode.core.config;

/**
 * 配置作用域枚举
 * 定义了配置项的作用域级别，优先级从低到高依次为：
 * SYSTEM < USER < PROJECT < RUNTIME
 * 
 * 查找顺序（从高优先级到低优先级）:
 * RUNTIME → PROJECT → USER → SYSTEM
 */
public enum ConfigScope {
    
    /**
     * 系统级配置
     * 路径: /etc/jwcode/config.yaml (Linux/Mac) 或 %ProgramData%/jwcode/config.yaml (Windows)
     * 优先级: 1 (最低)
     * 适用于: 全系统默认配置，由管理员设置
     */
    SYSTEM(1, "system", "/etc/jwcode/config.yaml", "%ProgramData%/jwcode/config.yaml"),
    
    /**
     * 用户级配置
     * 路径: ~/.jwcode/config.yaml
     * 优先级: 2
     * 适用于: 用户个人偏好设置
     */
    USER(2, "user", "~/.jwcode/config.yaml", null),
    
    /**
     * 项目级配置
     * 路径: ./.jwcode/config.yaml (当前工作目录)
     * 优先级: 3
     * 适用于: 项目特定配置，可被版本控制
     */
    PROJECT(3, "project", "./.jwcode/config.yaml", null),
    
    /**
     * 运行时配置
     * 路径: 内存中（不持久化）
     * 优先级: 4 (最高)
     * 适用于: 临时覆盖，命令行参数等
     */
    RUNTIME(4, "runtime", null, null);
    
    private final int priority;
    private final String name;
    private final String unixPath;
    private final String windowsPath;
    
    ConfigScope(int priority, String name, String unixPath, String windowsPath) {
        this.priority = priority;
        this.name = name;
        this.unixPath = unixPath;
        this.windowsPath = windowsPath;
    }
    
    /**
     * 获取优先级数值（数值越大优先级越高）
     */
    public int getPriority() {
        return priority;
    }
    
    /**
     * 获取作用域名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取 Unix/Linux/Mac 系统下的默认配置路径
     */
    public String getUnixPath() {
        return unixPath;
    }
    
    /**
     * 获取 Windows 系统下的默认配置路径
     */
    public String getWindowsPath() {
        return windowsPath;
    }
    
    /**
     * 获取当前系统的默认配置路径
     */
    public String getDefaultPath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return windowsPath != null ? windowsPath : unixPath;
        }
        return unixPath;
    }
    
    /**
     * 检查当前作用域是否比另一个作用域优先级更高
     */
    public boolean isHigherPriorityThan(ConfigScope other) {
        return this.priority > other.priority;
    }
    
    /**
     * 检查当前作用域是否比另一个作用域优先级更低
     */
    public boolean isLowerPriorityThan(ConfigScope other) {
        return this.priority < other.priority;
    }
    
    /**
     * 从名称解析作用域
     * @param name 作用域名称
     * @return 对应的 ConfigScope，如果未找到则返回 null
     */
    public static ConfigScope fromName(String name) {
        if (name == null) {
            return null;
        }
        for (ConfigScope scope : values()) {
            if (scope.name.equalsIgnoreCase(name)) {
                return scope;
            }
        }
        return null;
    }
    
    /**
     * 获取按优先级排序的作用域数组（从低到高）
     */
    public static ConfigScope[] getSortedByPriority() {
        return new ConfigScope[] { SYSTEM, USER, PROJECT, RUNTIME };
    }
    
    /**
     * 获取按优先级排序的作用域数组（从高到低）
     */
    public static ConfigScope[] getSortedByPriorityDesc() {
        return new ConfigScope[] { RUNTIME, PROJECT, USER, SYSTEM };
    }
    
    /**
     * 检查此作用域的配置是否持久化到文件
     */
    public boolean isPersistent() {
        return this != RUNTIME;
    }
    
    @Override
    public String toString() {
        return name;
    }
}
