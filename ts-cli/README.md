# @jwcode/cli — JWCode TypeScript CLI

> **JWCode** 是一款 AI 编程助手 CLI 工具，基于 TypeScript + React/Ink 构建，提供交互式终端界面（TUI），与 Java 后端通过 WebSocket 通信，实现 AI 驱动的代码辅助能力。

---

## 📦 安装

### 前置要求

| 依赖 | 最低版本 | 说明 |
|------|---------|------|
| Node.js | 18+ | 运行时环境 (推荐 20 LTS) |
| npm | 9+ | 包管理器 |
| Java | 17+ | 后端服务 (仅 `start` 命令需要) |

### 全局安装

```bash
npm install -g @jwcode/cli
```

### 本地开发安装

```bash
# 克隆项目
git clone <repo-url>
cd ts-cli

# 安装依赖
npm install

# 构建
npm run build

# 链接到全局 (可选)
npm link
```

---

## 🚀 快速开始

### 启动完整服务（后端 + TUI）

```bash
jwcode start
```

启动后端 Java 服务并打开交互式终端界面。

### 指定端口和工作目录

```bash
jwcode start -p 8080 -w /path/to/workspace
```

### 仅启动 TUI 客户端（连接到已有后端）

```bash
jwcode run -b http://localhost:8080
```

### 查看版本

```bash
jwcode version
```

---

## 📖 命令参考

### `jwcode start`

启动 Java 后端服务并打开交互式 TUI 界面。

| 参数 | 别名 | 类型 | 默认值 | 说明 |
|------|------|------|--------|------|
| `--port` | `-p` | number | `17340` | 后端服务端口 |
| `--backend` | `-B` | boolean | `false` | 仅启动后端，不启动 TUI |
| `--workspace` | `-w` | string | `cwd` | 工作目录路径 |

### `jwcode run`

仅启动 TUI 客户端，连接到已有的后端服务。

| 参数 | 别名 | 类型 | 默认值 | 说明 |
|------|------|------|--------|------|
| `--backend-url` | `-b` | string | `http://localhost:17340` | 后端 WebSocket URL |
| `--ws-url` | `--ws` | string | 派生自 `-b` | 直接指定 WebSocket URL |

### `jwcode version`

显示 CLI 版本号和构建信息。

---

## ⚙️ 配置

### 环境变量

| 变量名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `JWCODE_THEME` | `dark` / `light` | `dark` | 界面主题 |
| `JWCODE_THEME_COLORS` | JSON string | — | 自定义主题颜色（覆盖默认色） |
| `JWCODE_PORT` | number | `17340` | 默认后端端口 |
| `DEBUG` | `jwcode:*` | — | 启用调试日志 |

### 配置文件

配置文件位于 `~/.jwcode/config.json`，支持以下配置项：

```json
{
  "theme": "dark",
  "port": 17340,
  "workspace": "/path/to/default/workspace"
}
```

### 主题自定义

通过 `JWCODE_THEME_COLORS` 环境变量自定义颜色：

```bash
# 覆盖主题色
export JWCODE_THEME_COLORS='{"primary":"#00ff00","bg":"#1a1a2e"}'
```

---

## 🏗️ 项目架构

```
ts-cli/
├── src/
│   ├── main.ts                  # 入口文件
│   ├── App.tsx                  # React 根组件 (Ink)
│   ├── client.ts                # WebSocket 客户端
│   ├── config.ts                # 配置管理
│   ├── launcher.ts              # 后端进程启动器
│   ├── pasteBuffer.ts           # 粘贴缓冲区
│   ├── protocol.ts              # WebSocket 消息协议
│   ├── store.ts                 # 全局状态管理
│   ├── theme.ts                 # 主题系统
│   ├── commands/                # CLI 命令实现
│   ├── components/              # React/Ink UI 组件
│   │   ├── SetupWizard.tsx       # 安装向导
│   │   ├── CommandPalette.tsx    # 命令面板
│   │   ├── StatusLine.tsx        # 状态栏
│   │   ├── TextInput.tsx         # 文本输入框
│   │   ├── ApprovalModal.tsx     # 审批弹窗
│   │   ├── FilePalette.tsx       # 文件选择面板
│   │   └── PlanTaskBoard.tsx     # 计划任务面板
│   ├── hooks/                   # React Hooks
│   │   ├── useMouseWheel.ts
│   │   ├── useStreamHandlers.ts
│   │   └── useWebSocket.ts
│   └── __tests__/               # 单元测试
├── backend/                     # Java 后端（独立项目）
├── .github/workflows/ci.yml     # CI 工作流配置
├── build.mjs                    # esbuild 构建脚本
├── package.json
├── tsconfig.json
└── proguard.conf                # Java 后端混淆配置
```

### 架构流程图

```
┌─────────────────────────────────────────────┐
│          终端用户 (Terminal)                  │
└──────────────────┬──────────────────────────┘
                   │ stdin/stdout
┌──────────────────▼──────────────────────────┐
│       @jwcode/cli (TypeScript TUI)           │
│   ┌──────────┐  ┌──────────────────────┐    │
│   │  Commands │  │  React/Ink UI       │    │
│   │  (CLI)    │  │  (组件树)            │    │
│   └─────┬────┘  └──────────┬───────────┘    │
│         │                  │                  │
│   ┌─────▼──────────────────▼───────────┐    │
│   │        JwCodeClient                │    │
│   │     (WebSocket 客户端)              │    │
│   └─────────────────┬──────────────────┘    │
└─────────────────────┼───────────────────────┘
                      │ WebSocket (ws://)
┌─────────────────────▼───────────────────────┐
│          Java 后端服务                       │
│     (WebSocket Server + AI 引擎)            │
└─────────────────────────────────────────────┘
```

---

## 🔧 开发指南

### 开发环境搭建

```bash
# 安装依赖
npm install

# 启动开发模式（自动构建 + 运行）
npm run go

# 或者分步执行
npm run build        # 构建 CLI
node dist/cli.js run  # 运行 CLI
```

### 测试

```bash
# 运行所有测试
npm test

# 监听模式（开发时使用）
npm run test:watch

# 运行指定测试文件
npx vitest run src/__tests__/store.test.ts
```

### 构建

```bash
# 生产构建
npm run build

# 验证构建产物
node dist/cli.js version
```

构建产物输出到 `dist/cli.js`，使用 **esbuild** 打包为单个 ESM 文件，外部依赖不打包。

### 代码风格

- TypeScript: 严格模式 (`strict: true`)
- JSX: `react-jsx` 运行时
- 模块: ESM (`"type": "module"`)
- 缩进: 2 空格
- 命名规范:
  - 变量/函数: `camelCase`
  - 类/组件: `PascalCase`
  - 文件: `camelCase.ts` / `PascalCase.tsx`
  - 常量: `UPPER_SNAKE_CASE`

---

## 🧪 测试

项目使用 [Vitest](https://vitest.dev/) 作为测试框架。

### 现有测试

| 测试文件 | 覆盖模块 | 说明 |
|---------|---------|------|
| `store.test.ts` | `store.ts` | 全局状态管理单元测试 |
| `theme.test.ts` | `theme.ts` | 主题系统单元测试 |
| `pasteBuffer.test.ts` | `pasteBuffer.ts` | 粘贴缓冲区测试 |
| `tokenEstimate.test.ts` | 工具函数 | Token 估算测试 |

### 编写新测试

```typescript
// src/__tests__/example.test.ts
import { describe, it, expect } from 'vitest';
import { yourFunction } from '../yourModule.js';

describe('yourModule', () => {
  it('should do something', () => {
    expect(yourFunction()).toBe(expected);
  });
});
```

---

## 🚢 CI/CD

项目使用 **GitHub Actions** 进行持续集成。

### CI 工作流 (`.github/workflows/ci.yml`)

| 阶段 | 操作 | 说明 |
|------|------|------|
| Checkout | `actions/checkout@v4` | 检出代码 |
| Setup Node | `actions/setup-node@v4` | 配置 Node.js (18/20/22) |
| Install | `npm ci` | 安装依赖（锁定版本） |
| Type Check | `npx tsc --noEmit` | TypeScript 类型检查 |
| Test | `npm test` | 运行单元测试 |
| Build | `npm run build` | 构建打包 |
| Verify | 检查 dist/cli.js | 验证构建产物 |

### 触发条件

- `push` 到 `main` / `master` 分支
- `pull_request` 到 `main` / `master` 分支

---

## ❓ 常见问题

### 1. 启动时端口被占用

```bash
# 指定其他端口
jwcode start -p 8080
```

### 2. WebSocket 连接失败

确保后端服务已启动，检查 URL 是否正确：

```bash
# 指定后端地址
jwcode run -b http://localhost:17340
```

### 3. 构建后运行报错

```bash
# 清理后重新构建
rm -rf dist
npm run build
```

### 4. `EPIPE` / `ECONNRESET` 错误

这是终端断开时的正常行为，不影响程序运行。项目已自动处理这些信号。

---

## 📄 许可

本项目为 **内部项目**，未经授权不得分发或修改。

---

## 🏷️ 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 3.0.0 | — | 当前版本，TypeScript 重写，React/Ink TUI |
| 2.x | — | Python 版本 CLI |
| 1.x | — | 初版 CLI |
