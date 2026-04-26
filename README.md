# JwCode - Java 终端 AI 编码工具

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-red.svg)](https://maven.apache.org/)

> 用 Java 重构的终端 AI 编码工具，对标 TypeScript 版 Claude Code

## 📋 目录

- [特性](#-特性)
- [快速开始](#-快速开始)
- [项目结构](#-项目结构)
- [命令参考](#命令参考)
- [配置指南](#-配置指南)
- [开发指南](#-开发指南)
- [贡献指南](#-贡献指南)
- [许可证](#-许可证)

## ✨ 特性

### 核心功能
- 🤖 **多模型支持** - 支持 OpenAI 兼容 API，可配置多个模型提供商
- 🛠️ **40+ 工具** - 文件操作、代码搜索、Web 搜索、任务管理等
- 📝 **50+ 命令** - 丰富的 CLI 命令，支持交互式操作
- 💬 **流式响应** - 实时显示 AI 生成内容和思考过程
- 🌐 **Web UI** - 支持 WebSocket 实时通信

### 高级功能
- 🔀 **子 Agent Fork** - 并行执行多个子代理任务
- 🎨 **主题系统** - 支持自定义终端主题
- 📊 **成本追踪** - 实时监控 API 使用成本
- 💾 **会话记忆** - 智能压缩和记忆管理

## 🚀 快速开始

### 前置要求

- JDK 17 或更高版本
- Maven 3.8+
- OpenAI 兼容 API Key (支持 OpenAI、Claude、Moonshot 等)

### 安装

```bash
# 克隆项目
git clone https://github.com/your-org/jwcode.git
cd jwcode

# 构建项目
mvn clean install -DskipTests

# 运行
cd jwcode-cli/target
java -jar jwcode-cli-1.0.0-SNAPSHOT.jar
```

### 配置 API Key

创建配置文件 `~/.jwcode/config.yaml`:

```yaml
default-provider: moonshot
providers:
  moonshot:
    base-url: https://api.moonshot.cn/v1
    api-keys:
      - sk-your-api-key
    models:
      - id: kimi-k2.5
        name: kimi-k2.5
        temperature: 1
```

### 使用

```bash
# 启动交互模式
jwcode

# 直接执行命令
jwcode "帮我写一个 Hello World 程序"

# 使用命令
jwcode> help          # 查看帮助
jwcode> /cost         # 查看成本统计
jwcode> /theme dark   # 切换主题
```

## 📁 项目结构

```
jwcode/
├── pom.xml                    # 父 POM
├── jwcode-parent/             # 父模块
├── jwcode-common/            # 公共工具类
├── jwcode-core/               # 核心功能 (工具、LLM、会话管理)
├── jwcode-cli/                # 命令行界面
├── jwcode-repl/               # REPL 交互环境
├── jwcode-web/                # Web UI 和 API
├── jwcode-distribution/       # 发布配置
├── docs/                     # 文档
├── AGENTS.md                 # AI 工程规范
└── docs/examples/            # 示例代码
```

### 模块说明

| 模块 | 说明 |
|------|------|
| `jwcode-common` | 公共工具类、常量、工具类 |
| `jwcode-core` | 核心：LLM、工具系统、会话管理、Agent |
| `jwcode-cli` | 命令行界面、命令解析 |
| `jwcode-repl` | 交互式编程环境 |
| `jwcode-web` | Web UI、WebSocket 服务 |

## 📖 命令参考

### 基础命令

| 命令 | 说明 |
|------|------|
| `/help` | 显示帮助信息 |
| `/exit` | 退出程序 |
| `/clear` | 清除对话历史 |
| `/config` | 配置管理 |
| `/cost` | 查看 API 使用成本 |
| `/diff` | 显示代码差异 |
| `/summary` | 生成会话摘要 |

### 文件操作

| 命令 | 说明 |
|------|------|
| `/files` | 列出项目文件 |
| `/copy` | 复制内容到剪贴板 |
| `/export` | 导出对话记录 |

### 开发工具

| 命令 | 说明 |
|------|------|
| `/plan` | 任务规划 |
| `/todo` | 任务管理 |
| `/compact` | 压缩上下文 |
| `/doctor` | 健康检查 |

### AI 功能

| 命令 | 说明 |
|------|------|
| `/agent` | 启动 Agent |
| `/agents` | 管理多个 Agent |
| `/skill` | 使用技能 |
| `/web` | 启动 Web UI |

完整命令列表请查看 [docs/FEATURES_GUIDE.md](docs/FEATURES_GUIDE.md)

## ⚙️ 配置指南

### 配置文件位置

- **全局配置**: `~/.jwcode/config.yaml`
- **系统提示词**: `~/.jwcode/system-prompt.md`
- **技能目录**: `~/.jwcode/skills/`
- **Agent 配置**: `~/.jwcode/agents/`

### 完整配置示例

```yaml
# ~/.jwcode/config.yaml
default-provider: moonshot

providers:
  moonshot:
    base-url: https://api.moonshot.cn/v1
    api-keys:
      - sk-your-api-key
    models:
      - id: kimi-k2.5
        name: kimi-k2.5
        temperature: 1
        max-tokens: 32000

  openai:
    base-url: https://api.openai.com/v1
    api-keys:
      - sk-your-key
    models:
      - id: gpt-4o
        name: gpt-4o
        temperature: 0.7

# 工具配置
tools:
  enabled:
    - FileReadTool
    - FileEditTool
    - GlobTool
    - GrepTool
    - BashTool
  disabled: []

# 主题配置
theme:
  name: default
  colors:
    primary: "#00D4AA"
    secondary: "#6366F1"
```

详细配置说明请查看 [docs/CONFIG_GUIDE.md](docs/CONFIG_GUIDE.md)

## 🔧 开发指南

### 环境要求

- JDK 17+
- Maven 3.8+
- IntelliJ IDEA (推荐)

### 构建

```bash
# 完整构建
mvn clean install

# 跳过测试
mvn clean install -DskipTests

# 仅构建指定模块
mvn clean install -pl jwcode-cli -am
```

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行指定测试类
mvn test -Dtest=CalculatorTest

# 生成覆盖率报告
mvn test jacoco:report
```

### 代码规范

```bash
# 代码格式化
mvn spotless:format

# 检查代码格式
mvn spotless:check
```

详细开发指南请查看 [docs/developer-guide.md](docs/developer-guide.md)

## 📚 文档

| 文档 | 说明 |
|------|------|
| [AGENTS.md](AGENTS.md) | AI 工程规范和最佳实践 |
| [docs/developer-guide.md](docs/developer-guide.md) | 完整开发者指南 |
| [docs/FEATURES_GUIDE.md](docs/FEATURES_GUIDE.md) | 功能使用指南 |
| [docs/CONFIG_GUIDE.md](docs/CONFIG_GUIDE.md) | 配置详细说明 |
| [docs/GAP_ANALYSIS.md](docs/GAP_ANALYSIS.md) | 与 Claude Code 的功能对比 |

## 🤝 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

### 开发规范

- 遵循 [AGENTS.md](AGENTS.md) 中的代码规范
- 所有新功能需要附带测试
- 更新相关文档
- 确保 `mvn test` 通过

## 📄 许可证

本项目基于 [Apache License 2.0](LICENSE) 许可证开源。

## 🙏 致谢

- 对标 [Claude Code](https://docs.anthropic.com/claude-code) 项目
- 使用 [JLine3](https://github.com/jline/jline3) 实现终端功能
- 使用 [Lanterna](https://github.com/mabe02/lanterna) 实现 UI 组件

---

**版本**: 1.0.0-SNAPSHOT  
**最后更新**: 2026-04-24
