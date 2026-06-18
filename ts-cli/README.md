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
| `JWCODE_THEME` | `dark` / `light` | 自适应 | 界面主题；未设置时按终端背景自适应检测，检测失败回退 `dark` |
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

JWCode 采用语义化颜色键，所有 UI 元素从主题读取颜色，可用 `JWCODE_THEME_COLORS` 覆盖任意键。

**主题选择优先级**：`JWCODE_THEME` 环境变量 > 终端背景自适应检测 > `dark`。

- `JWCODE_THEME=light` / `dark`：强制指定，跳过检测。
- 未设置时调用 `detectTerminalBg()`：通过 OSC 11 查询终端背景色，按亮度 `0.299R+0.587G+0.114B > 128` 判断 light/dark；查询有 50ms 超时，失败或非 TTY 回退 `dark`；Windows 下同步读取不可靠，直接回退 `dark`。

**语义颜色键**（dark / light 默认值）：

| 键 | dark | light | 用途 |
|----|------|-------|------|
| `toolName` | `magentaBright` | `magenta` | 工具名 |
| `toolArgs` | `cyan` | `cyan` | 工具参数 |
| `toolResult` | `green` | `green` | 工具结果 |
| `filePath` | `blueBright` | `blue` | 文件路径高亮 |
| `stepTitle` | `yellow` | `yellow` | 步骤标题 |
| `stepAction` | `yellow` | `yellow` | 步骤动作 |
| `stepThought` | `blue` | `blue` | 步骤思考 |

旧键（`primary`/`success`/`warning`/`error`/`tool` 等）全部保留，不破坏现有引用。

```bash
# 覆盖主题色（基础键）
export JWCODE_THEME_COLORS='{"primary":"#00ff00","bg":"#1a1a2e"}'

# 覆盖语义键
export JWCODE_THEME_COLORS='{"toolName":"#ff00ff","filePath":"#00aaff"}'

# 强制亮色主题
export JWCODE_THEME=light
```

---

## 🎨 视觉特性

JWCode TUI 参考 codex 的视觉风格，在纯前端层做了以下增强（不涉及协议/后端）：

### 自适应主题

启动时自动检测终端背景明暗并切换 dark/light 主题（见上文「主题自定义」）。在支持 OSC 11 的现代终端（Windows Terminal、iTerm2 等）上生效。

### 处理动画：Braille 旋转 + shimmer 色带

工具调用与步骤处于 `running` 状态时：

- 状态图标显示 Braille 旋转帧（`⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏`），替代静态 `[..]`。
- 工具名/步骤标题叠加 shimmer：一条余弦强度色带以 2s 周期扫过文本。truecolor 终端按强度做 RGB 混合，非 truecolor 退化为 dim/normal/bold 三级，不崩溃。
- 完成/错误仍显示 `[ok]` / `[!!]`。

相关 Hook：`useSpinner`、`useShimmer`。运行期间仅重渲染当前 streaming 消息的组件，已渲染的 `Static` 消息不受影响。

### 启动页像素动物动画

欢迎页（无消息且非生成态）左侧显示多帧 ASCII 像素动物（猫/狗/兔/鸟/熊/企鹅），每只 2-3 帧呼吸/眨眼动画，右侧显示版本、模型、工作目录与该动物的提示。视口过小（宽 < 60 或高 < 20）时自动隐藏动画，仅显示文字。

`Ctrl+.` 可随机切换到另一只动物（仅在欢迎空闲态响应，生成中或弹窗打开时不响应）。

### Hook 审批弹窗

工具调用触发审批时，弹窗采用 codex 式布局：

- 标题提问句（按工具类型：命令执行 / 文件写入 / 网络请求 / 通用工具）。
- 风险等级色带（CRITICAL 红 / HIGH 黄 / MEDIUM 青 / LOW 灰，黑字）。
- 单边框预览区：文件路径用 `filePath` 色着色；CRITICAL/HIGH 时危险关键字（`rm -rf`/`sudo`/`push` 等）用 `error` 色着色。
- 编号选项列表：选中项 `▶` 前缀 + 高亮加粗，每项标注快捷键（y/n/s/r）。
- 倒计时 10 格 block 进度条，≤5s 变红。
- footer 提示行：`1/2/3/4 · ↑↓ · Enter · Esc`。

### 路径高亮

工具参数、工具结果、步骤结果中的文件路径（POSIX 绝对路径、Windows 盘符路径、相对多段路径、带扩展名的文件名）自动用 `filePath` 色着色，便于快速定位。

### 键盘快捷键

| 快捷键 | 作用 | 生效条件 |
|--------|------|----------|
| `/` | 打开命令面板 | 输入框为空时 |
| `@` | 引用文件 | 输入框 |
| `↑` / `↓` | 历史记录 / 滚动 | 非弹窗、非面板态 |
| `Tab` | 切换 Plan 模式 | — |
| `Ctrl+E` | 展开/折叠工具调用 | — |
| `Ctrl+.` | 切换启动页动物 | 仅欢迎空闲态 |
| `Esc` | 暂停/停止生成（连按两次停止） | 生成中 |
| `Esc` | 关闭帮助/拒绝审批 | — |
| `PgUp` / `PgDn` | 翻页滚动 | — |
| `1`/`2`/`3`/`4` | 审批选项 | 审批弹窗打开时 |
| `y` / `n` | 允许 / 拒绝 | 审批弹窗打开时 |

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
│   │   ├── ApprovalModal.tsx     # 审批弹窗（codex 式）
│   │   ├── FilePalette.tsx       # 文件选择面板
│   │   └── PlanTaskBoard.tsx     # 计划任务面板
│   │   ├── ChatArea.tsx          # 聊天区（含 spinner/shimmer/路径高亮）
│   │   ├── PixelArt.tsx          # 启动页像素动物帧
│   │   └── highlightPaths.ts     # 路径分段着色工具
│   ├── hooks/                   # React Hooks
│   │   ├── useAppState.ts        # 状态订阅（selector + shallow equal）
│   │   ├── useMouseWheel.ts
│   │   ├── useStreamHandlers.ts
│   │   ├── useWebSocket.ts
│   │   ├── useKeyboardInput.ts   # 全局快捷键（含 Ctrl+. 切换动物）
│   │   ├── useCommandHandler.ts  # 命令派发
│   │   ├── useQueryGuard.ts      # 查询守卫
│   │   ├── useSpinner.ts         # Braille 旋转动画
│   │   ├── useShimmer.ts         # shimmer 色带
│   │   └── useAsciiAnimation.ts  # 像素动物多帧动画
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
| `theme.test.ts` | `theme.ts` | 主题系统单元测试（含语义键、自适应检测优先级） |
| `highlightPaths.test.ts` | `highlightPaths.ts` | 路径分段着色测试 |
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
