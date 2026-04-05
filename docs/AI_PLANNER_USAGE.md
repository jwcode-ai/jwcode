# AI 驱动任务规划 - 使用指南

## 🚀 快速开始

JWCode 的 AI 驱动任务规划功能已集成到系统中，可以通过以下命令使用：

### 1. AI 深度分析

```bash
jwcode> advanced ai-analyze "重构用户认证模块"
```

输出示例：
```
╔════════════════════════════════════════════════════════╗
║     🤖 AI 深度任务分析                                  ║
╚════════════════════════════════════════════════════════╝

📋 意图分析
   类型: REFACTOR (置信度: 85%)
   目标文件: [UserService.java, AuthController.java]

📊 复杂度评估: 7/10 (HIGH)
   技术复杂度: 7/10
   依赖复杂度: 8/10

⚠️ 风险评估: MEDIUM
   • 可能影响现有登录功能 [MEDIUM]

💡 建议使用 AI 规划执行此任务
  命令: advanced ai-plan "重构用户认证模块"
```

### 2. AI 任务规划

```bash
jwcode> advanced ai-plan "实现订单管理功能"
```

输出示例：
```
╔════════════════════════════════════════════════════════╗
║     🤖 AI 驱动任务规划                                  ║
╚════════════════════════════════════════════════════════╝

任务: 实现订单管理功能
正在使用 AI 分析任务意图、复杂度、风险...

📋 意图分析
   类型: CREATE (置信度: 90%)
...

📊 复杂度评估: 8/10 (HIGH)
...

执行步骤 (共 6 步):
1. 分析需求 (@analyzer)
   理解订单管理功能需求
...

🔗 依赖分析
关键路径: task-1 -> task-2 -> task-3
并行分组: 3 组

✓ AI 规划完成！

使用 'advanced ai-execute "实现订单管理..."' 执行此计划
```

### 3. AI 规划并执行

```bash
jwcode> advanced ai-execute "重构所有 API 为异步模式"
```

### 4. 简化命令

```bash
# 使用 plan 命令（自动使用 AI 模式）
jwcode> plan "实现用户认证功能"

# AI 分析
jwcode> plan ai "分析代码结构"

# AI 规划
jwcode> plan ai-plan "重构数据库访问层"
```

---

## 📋 命令速查表

| 命令 | 功能 | 示例 |
|------|------|------|
| `advanced ai-analyze <task>` | AI 深度分析任务 | `advanced ai-analyze "重构代码"` |
| `advanced ai-plan <task>` | AI 规划任务 | `advanced ai-plan "实现功能"` |
| `advanced ai-execute <task>` | AI 规划并执行 | `advanced ai-execute "修复bug"` |
| `plan <task>` | 快捷规划（AI 模式） | `plan "重构代码"` |
| `plan ai <task>` | 快捷分析 | `plan ai "分析项目"` |

---

## 🔧 自动触发

### 自动检测复杂任务

系统会自动检测复杂任务并建议使用 AI 规划：

```bash
jwcode> refactor all API calls to async  # 自动提示使用 AI 规划
```

输出：
```
⚠️  检测到复杂任务（复杂度: 8/10）

建议使用 AI 规划执行此任务：
  advanced ai-plan "refactor all API calls to async"

原因:
  • 涉及多个文件修改
  • 需要保持向后兼容
  • 需要全面测试验证
```

---

## 📊 查看状态

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
   快捷键: Tab

⚡ YOLO Mode (全自动):
   ○ 关闭
   参数: --yolo

🐝 Agent Swarm (智能体集群):
   总 Agents: 8
   活跃: 0
   完成任务: 12

🤖 AI Planner (AI 规划器):
   已学习模式: 45
   平均成功率: 87%

可用命令:
  advanced thinking    - 切换深度推理模式
  advanced yolo        - 切换全自动模式
  advanced ai-plan     - AI 驱动任务规划
  advanced ai-analyze  - AI 深度任务分析
  advanced ai-execute  - AI 规划并执行
  ...
```

---

## 💡 使用建议

### 何时使用 AI 规划？

✅ **建议使用 AI 规划的场景：**
- 重构涉及多个文件
- 实现复杂功能模块
- 项目级别架构调整
- 批量代码迁移
- 多步骤自动化任务

❌ **不需要 AI 规划的场景：**
- 简单的问答
- 单行代码修改
- 快速查询信息
- 互动式对话

### 复杂度阈值

| 复杂度 | 说明 | 建议 |
|--------|------|------|
| 1-3 | 简单任务 | 普通模式即可 |
| 4-6 | 中等任务 | 可以使用 AI 规划 |
| 7-8 | 复杂任务 | **强烈建议** AI 规划 |
| 9-10 | 极复杂 | **必须** AI 规划 + 人工确认 |

---

## 🔄 工作流程

### 标准 AI 规划流程

```
用户输入: "重构用户认证模块"

        ↓
   AI 深度分析
   - 意图识别: REFACTOR
   - 复杂度: 7/10
   - 风险: MEDIUM
   - 预估: 6个子任务
        ↓
   动态任务分解
   - 分析需求
   - 设计架构
   - 生成代码
   - 代码审查
   - 编写测试
   - 验证结果
        ↓
   智能依赖分析
   - 关键路径: 6步
   - 可并行: 2组
        ↓
   并行执行
   - 创建子 Agent
   - 监控执行
   - 动态调整
        ↓
   结果聚合
   - 汇总结果
   - 执行报告
   - 学习记录
```

---

## 🛠️ 故障排除

### AI 分析失败

如果 AI 分析失败，系统会自动回退到规则模式：

```
[PlanCmd] AI 规划失败: Connection timeout
[PlanCmd] 回退到规则模式
```

### 重规划触发

当执行失败率超过 30% 时，会自动触发重规划：

```
[DynamicExecutionEngine] 触发重规划
[DynamicExecutionEngine] 重规划策略: SUBDIVIDE
[DynamicExecutionEngine] 使用新计划继续执行
```

---

## 📝 示例场景

### 场景 1：代码重构

```bash
# 分析
jwcode> advanced ai-analyze "将所有同步 API 改为异步"

# 规划
jwcode> advanced ai-plan "将所有同步 API 改为异步"

# 执行
jwcode> advanced ai-execute "将所有同步 API 改为异步"
```

### 场景 2：功能开发

```bash
# 一键规划并执行
jwcode> plan ai-plan "实现用户订单管理功能"
```

### 场景 3：代码审查

```bash
# 分析项目结构
jwcode> plan ai "分析项目架构问题"
```

---

现在 AI 驱动任务规划功能已完全集成到 JWCode 中，可以直接使用！
