# JWCode

[![Java](https://img.shields.io/badge/Java-17+-brightgreen?style=flat-square)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](LICENSE)

## 项目简介

JWCode 是一个用 Java 重构的终端 AI 编码工具，完全参照 Claude Code 的并行设计。它理解您的代码库，通过自然语言命令帮助您更快地编写代码。

## 功能特性

- **终端交互式 AI 对话** - 在终端中直接与 AI 助手对话
- **代码库理解** - 自动理解和分析项目结构
- **文件操作** - 读取、编辑、写入文件
- **终端命令执行** - 安全地执行 shell 命令
- **Git 工作流管理** - 处理 Git 操作和分支管理
- **MCP 服务器集成** - 支持 Model Context Protocol
- **插件系统** - 可扩展的插件架构
- **技能系统** - 内置和自定义技能支持
- **多代理协作** - 支持多代理协同工作

## 快速开始

### 系统要求

- Java 17 或更高版本
- Maven 3.8+
- Git

### 安装

```bash
# 克隆项目
git clone https://github.com/your-org/jwcode.git
cd jwcode

# 构建项目
mvn clean install

# 运行
java -jar jwcode-cli/target/jwcode-cli-1.0.0.jar
```

### 基本使用

```bash
# 交互模式
jwcode

# 非交互模式（打印输出）
jwcode -p "解释这个项目的结构"

# 继续上次会话
jwcode --continue

# 使用特定模型
jwcode --model sonnet "帮我写一个排序算法"
```

## 项目结构

```
jwcode/
├── jwcode-parent/          # 父 POM，依赖管理
├── jwcode-core/            # 核心引擎
├── jwcode-cli/             # 命令行界面
├── jwcode-repl/            # REPL 引擎
├── jwcode-sdk/             # SDK 桥接
├── jwcode-mcp/             # MCP 服务
├── jwcode-plugin/          # 插件系统
├── jwcode-skill/           # 技能系统
├── jwcode-lsp/             # LSP 服务
├── jwcode-analytics/       # 分析服务
├── jwcode-common/          # 公共工具类
└── jwcode-distribution/    # 分发打包
```

## 核心模块说明

### jwcode-core（核心引擎）

包含查询引擎、工具系统、会话管理和任务管理。

```java
// 示例：使用查询引擎
QueryEngine engine = new QueryEngine.Builder()
    .withSession(session)
    .withTools(tools)
    .build();

QueryResult result = engine.query(request).join();
```

### jwcode-cli（命令行界面）

提供命令行解析和命令执行功能。

```bash
# 常用命令
jwcode mcp list          # 列出 MCP 服务器
jwcode plugin install    # 安装插件
jwcode auth login        # 登录认证
```

### jwcode-mcp（MCP 服务）

支持 Model Context Protocol，可以连接外部 MCP 服务器。

```json
{
  "mcpServers": {
    "filesystem": {
      "type": "stdio",
      "command": "mcp-server-filesystem",
      "args": ["/workspace"]
    }
  }
}
```

## 开发指南

### 添加新工具

1. 实现 `Tool` 接口
2. 在 `ToolRegistry` 中注册
3. 编写单元测试

```java
public class MyCustomTool implements Tool<MyInput, MyOutput, MyProgress> {
    @Override
    public String getName() {
        return "MyCustomTool";
    }
    
    @Override
    public CompletableFuture<ToolResult<MyOutput>> call(
        MyInput args,
        ToolContext context,
        CanUseToolFn canUseTool,
        AssistantMessage parentMessage,
        Consumer<ToolProgress<MyProgress>> onProgress
    ) {
        // 实现工具逻辑
    }
}
```

### 开发插件

1. 创建插件清单文件 `plugin.json`
2. 实现插件入口类
3. 打包并发布

## 配置说明

### 全局配置

配置文件位置：`~/.jwcode/config.json`

```json
{
  "model": "sonnet",
  "theme": "dark",
  "verbose": false,
  "autoUpdate": true
}
```

### 项目配置

配置文件位置：`.jwcode/settings.json`

```json
{
  "model": "opus",
  "permissionMode": "auto",
  "mcpServers": ["filesystem", "git"]
}
```

## 贡献指南

欢迎贡献代码、报告问题或提出建议！

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

本项目采用 Apache 2.0 许可证 - 详见 [LICENSE](LICENSE) 文件。

## 联系方式

- 项目主页：[GitHub](https://github.com/your-org/jwcode)
- 问题反馈：[Issues](https://github.com/your-org/jwcode/issues)

## 致谢

JWCode 的设计灵感来源于 Claude Code，感谢 Anthropic 团队的优秀工作。