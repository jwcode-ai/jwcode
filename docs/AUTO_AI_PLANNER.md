# 自动 AI 规划模式 - 使用指南

## 概述

JWCode 现在支持**自动检测任务复杂度**并智能选择处理方式：
- **简单任务** → 普通单 Agent 模式（快速响应）
- **复杂任务** → AI 规划模式（并行执行、自动重规划）

无需手动选择命令，系统会自动判断！

---

## 🚀 使用方法

### 1. 开启自动模式

```bash
jwcode> advanced auto-ai
```

输出：
```
╔════════════════════════════════════════════════════════╗
║     自动 AI 规划模式已开启                              ║
╚════════════════════════════════════════════════════════╝

JwCode 将自动检测复杂任务并使用 AI 规划执行

检测维度:
  • 关键词匹配（refactor, implement, migrate...）
  • 描述长度（超过 100 字符加分）
  • 涉及文件数量
  • 操作类型复杂度
  • 上下文复杂度

阈值设置:
  • 简单任务（1-3分）: 普通模式
  • 中等任务（4-6分）: 根据阈值判断
  • 复杂任务（7-10分）: 强制 AI 规划
```

### 2. 正常使用（自动判断）

开启自动模式后，直接输入任务描述：

```bash
# 简单任务 → 普通模式
jwcode> 你好
jwcode> 解释一下这段代码
jwcode> 如何修复 NullPointerException

# 复杂任务 → 自动使用 AI 规划
jwcode> 重构用户认证模块
jwcode> 实现订单管理功能
jwcode> 将所有同步 API 改为异步
```

### 3. 查看当前模式

```bash
jwcode> advanced status
```

输出：
```
╔════════════════════════════════════════════════════════╗
║     高级功能状态                                       ║
╚════════════════════════════════════════════════════════╝

🧠 Thinking Mode (深度推理):
   ● 开启

⚡ YOLO Mode (全自动):
   ○ 关闭

🐝 Agent Swarm (智能体集群):
   总 Agents: 8
   活跃: 0
   完成任务: 12

🤖 AI Auto Planner (自动规划):
   ● 开启  ← 自动模式已开启
   自动检测复杂任务并使用 AI 规划

可用命令:
  advanced auto-ai     - 切换自动 AI 规划模式
  ...
```

---

## 📊 复杂度评分机制

### 评分维度

| 维度 | 说明 | 加分 |
|------|------|------|
| **高复杂度关键词** | refactor, migrate, architecture, security | +2/个 |
| **中等复杂度关键词** | add, fix, update, document | +1/个 |
| **描述长度** | 超过 50/100/200 字符 | +1/+2/+3 |
| **文件引用** | 涉及 1-2/3-5/5+ 个文件 | +1/+2/+3 |
| **步骤暗示** | first, then, next, 第一步 | +1/个 |
| **上下文** | 会话消息 10+/20+ | +1/+2 |

### 复杂度等级

| 分数 | 等级 | 处理方式 |
|------|------|----------|
| 0-3 | 简单 | 普通模式 |
| 4-6 | 中等 | 阈值判断（默认≥5用AI规划） |
| 7-10 | 复杂 | 强制 AI 规划 |

---

## 🎯 触发示例

### 自动使用 AI 规划的情况

```bash
# 关键词触发
jwcode> refactor all API calls to async
# 检测到 "refactor" → 复杂度 +2

# 多文件触发
jwcode> update UserService.java, OrderService.java and PaymentService.java
# 检测到 3 个文件 → 复杂度 +2

# 详细描述触发
jwcode> implement a complete user authentication system with JWT token support, 
         including login, logout, token refresh and role-based access control
# 长度 > 200 → 复杂度 +3

# 架构级别触发
jwcode> redesign the system architecture to support microservices
# 检测到 "architecture" → 复杂度 +2
```

### 使用普通模式的情况

```bash
# 问候
jwcode> 你好
# 简单模式 → 普通模式

# 问答
jwcode> 什么是 Spring Boot?
# 简单模式 → 普通模式

# 简单修改
jwcode> 修复这个 NullPointerException
# 中等模式（可能）→ 根据分数判断

# 解释代码
jwcode> 解释一下这段代码的作用
# 简单模式 → 普通模式
```

---

## 🔧 手动控制

即使开启了自动模式，也可以手动指定模式：

### 强制使用 AI 规划

```bash
jwcode> advanced ai-plan "重构代码"
```

### 强制使用普通模式

直接输入简单命令即可，或：

```bash
jwcode> advanced ai-analyze "任务"  # 仅分析，不执行
```

### 关闭自动模式

```bash
jwcode> advanced auto-ai
# 切换为关闭状态
```

---

## 📈 实际效果

### 场景 1：简单问答

```bash
jwcode> 什么是依赖注入？
```

系统判断：
- 复杂度：1/10（简单问答）
- 模式：普通模式
- 响应：快速直接回答

### 场景 2：复杂重构

```bash
jwcode> 将所有同步 API 改为异步模式，并确保向后兼容
```

系统判断：
- 复杂度：8/10（检测到 refactor, all, API, 长描述）
- 模式：AI 规划模式
- 响应：
  ```
  🤖 检测到复杂任务（8/10），使用 AI 规划模式...
  
  📋 意图分析: REFACTOR
  📊 复杂度: 8/10 (HIGH)
  ⏱️ 预估: 6个子任务，15分钟
  
  正在自动分解任务并并行执行...
  ```

---

## 💡 最佳实践

### 1. 建议开启自动模式

```bash
jwcode> advanced auto-ai  # 开启后无需关心选择模式
```

### 2. 详细描述复杂任务

详细描述有助于系统正确判断：

```bash
# 好的描述（会触发 AI 规划）
jwcode> 重构 UserService 类，提取用户验证逻辑到单独的 Validator 类，
         并添加单元测试确保功能正确

# 简单的描述（可能不会触发）
jwcode> 重构 UserService
```

### 3. 查看分析结果

```bash
jwcode> advanced ai-analyze "你的任务"
# 查看复杂度分析，了解系统如何评估
```

---

## 🔍 故障排除

### 复杂任务没有被识别？

1. 添加更多关键词：`refactor`, `implement`, `migrate`, `architecture`
2. 增加描述长度，提供更多上下文
3. 手动使用：`advanced ai-plan "任务"`

### 简单任务被误判为复杂？

1. 简化描述，避免使用复杂关键词
2. 关闭自动模式，手动选择模式
3. 使用普通 QueryEngine 直接提问

### 查看详细分析

```bash
jwcode> advanced ai-analyze "你的任务描述"
```

输出：
```
╔══════════════════════════════════════════════════════════╗
║              🤖 自动复杂度分析                           ║
╚══════════════════════════════════════════════════════════╝

任务: 重构用户认证模块...

复杂度: HIGH (8/10)
阈值: 5
建议模式: AI 规划

原因:
  • 高复杂度操作
  • 涉及 3 个文件
  • 详细描述（156 字符）
  • 暗示多步骤（2 个）

分析耗时: 12ms
```

---

## 🎉 总结

现在你只需要：

1. **开启自动模式**：`advanced auto-ai`
2. **正常输入任务**：系统会自动判断
3. **享受智能体验**：简单任务快速响应，复杂任务 AI 规划

无需记忆命令，无需手动选择，JWCode 会为你做出最佳选择！
