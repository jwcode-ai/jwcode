# @jwcode/cli — JWCode TypeScript CLI

> **JWCode** 是一款 AI 编程助手 CLI 工具，基于 TypeScript + Solid/OpenTUI 构建，提供交互式终端界面（TUI），与 Java 后端通过 WebSocket 通信，实现 AI 驱动的代码辅助能力。

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

### 镜像安装（任选其一）

本包在以下 15 个 npm 组织同步发布，版本号完全一致，可根据偏好任选一个安装：

```bash
npm install -g @jwcode/cli       # jwcode
npm install -g @zhipucode/cli    # zhipucode
npm install -g @aliclaw/cli      # aliclaw
npm install -g @zhupuclaw/cli    # zhupuclaw
npm install -g @kimicode/cli     # kimicode
npm install -g @minimaxcode/cli  # minimaxcode
npm install -g @alicode/cli      # alicode
npm install -g @huaweiyun/cli    # huaweiyun
npm install -g @tencentclaw/cli  # tencentclaw
npm install -g @deepseekclaw/cli # deepseekclaw
npm install -g @tencentcode/cli  # tencentcode
npm install -g @deepseekcode/cli # deepseekcode
npm install -g @deepclaw/cli     # deepclaw
npm install -g @minimaxclaw/cli  # minimaxclaw
npm install -g @hyclaw/cli       # hyclaw
```

`@jwcode/cli` 是规范版本（canonical），所有 15 个包共享同一份代码和 GitHub Release 资源，行为完全相同。
安装后可通过对应命令名启动（如 `aliclaw start`、`zhipucode run`）。

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
│   ├── main.tsx                  # 入口 (Bun 打包, Solid+OpenTUI)
│   ├── client.ts                 # WebSocket 客户端
│   ├── config.ts                 # 配置加载
│   ├── launcher.ts               # Java 后端进程管理
│   ├── pasteBuffer.ts            # 粘贴缓冲
│   ├── fuzzyMatch.ts             # 文件名模糊匹配
│   ├── textInputGrapheme.ts      # grapheme 感知输入
│   ├── protocol.ts               # WebSocket 消息协议
│   ├── update.ts                 # npm 版本自检
│   ├── commands/                 # 斜杠命令定义
│   ├── solid/
│   │   ├── components/           # 17 个 UI 组件 (含 messages/ 子目录)
│   │   ├── hooks/                # AppStateProvider, ClientProvider, ...
│   │   ├── context/              # theme, kv, language, route, ...
│   │   ├── ui/                   # dialog, toast
│   │   ├── util/                 # clipboard, keybind, ...
│   │   ├── i18n/                 # en, zh
│   │   └── theme/                # 33 个 JSON 主题
│   └── __tests__/                # vitest
├── backend/                      # 打包后的 Java jar + jre/
├── .github/workflows/ci.yml      # CI 工作流配置
├── build.mjs                     # Bun + ProGuard + jlink 构建
├── package.json
├── tsconfig.json
└── proguard.conf                 # Java 后端混淆配置
```

### 架构流程图

```
┌────────────────────────────────────────────┐
│          终端用户 (Terminal)                 │
└──────────────────┬─────────────────────────┘
                   │ stdin/stdout
┌──────────────────▼─────────────────────────┐
│      @jwcode/cli (TypeScript TUI)           │
│  ┌──────────┐  ┌───────────────────────┐   │
│  │  Commands │  │  Solid/OpenTUI UI     │   │
│  │  (CLI)    │  │  (12 个组件 + 33 主题) │   │
│  └─────┬────┘  └──────────┬────────────┘   │
│        │                  │                  │
│  ┌─────▼──────────────────▼────────────┐   │
│  │  Providers: AppState/Client/KV/Theme │   │
│  └─────────────────┬───────────────────┘   │
│                    │                        │
│  ┌─────────────────▼───────────────────┐   │
│  │        JwCodeClient                  │   │
│  │     (WebSocket 客户端)               │   │
│  └─────────────────┬───────────────────┘   │
└────────────────────┼──────────────────────┘
                     │ WebSocket (ws://)
┌────────────────────┼──────────────────────┐
│                    ▼                       │
│          Java 后端服务 (WebSocket + AI)     │
└───────────────────────────────────────────┘
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

构建产物输出到 `dist/cli.js`，使用 **Bun bundler** + `@opentui/solid/bun-plugin` 打包，外部依赖不打包。

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
| `store.test.ts` | `useStreamHandlers` | appendMessage 队列及 cleanArgs 测试 |
| `theme.test.ts` | `context/theme` | 33 主题 JSON 校验及颜色引用验证 |
| `pasteBuffer.test.ts` | `pasteBuffer.ts` | 粘贴缓冲区测试 |
| `tokenEstimate.test.ts` | `TextInput` | Token 估算测试 (CJK/English) |

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

### 3. 鼠标右键/粘贴

- **有选中文本时右键**：复制选中内容到剪贴板
- **无选中文本时右键**：从剪贴板粘贴内容到输入框
- **长按左键（300ms）**：选中光标处单词并自动复制

### 4. @ 文件引用

输入 @ 后自动弹出文件选择面板，支持：
- 通过后端 API 获取文件列表
- API 不可用时自动回退到本地文件系统扫描（最多 3 层，跳过 node_modules/.git 等目录）

### 5. 退出行为

CLI 退出时（Ctrl+C 或 /exit）自动执行：
1. 通过 WebSocket 发送 exit 信号通知后端清理会话
2. 优雅关闭 WebSocket 连接
3. 优雅关闭 Java 后端进程（先 taskkill 再 /F 兜底）
4. 清理 daemon 缓存文件

### 6. 构建后运行报错

```bash
# 清理后重新构建
rm -rf dist
npm run build
```

### 7. `EPIPE` / `ECONNRESET` 错误

这是终端断开时的正常行为，不影响程序运行。项目已自动处理这些信号。

---

## 📄 许可

本项目为 **内部项目**，未经授权不得分发或修改。

---

## 🏷️ 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 3.1.0 | 2026-06 | 消息组件拆分、输入框修复、右键粘贴、退出同步 |
| 3.0.0 | — | 当前版本，TypeScript 重写，Solid/OpenTUI TUI |
| 2.x | — | Python 版本 CLI |
| 1.x | — | 初版 CLI |
