# Agent Swarm 用户指南

## 概述

Agent Swarm 是 JwCode 的高级功能，模拟 Kimi K2.5 的智能体集群能力。

## 当前实现状态

### ✅ 已实现功能

| 功能 | 状态 | 说明 |
|------|------|------|
| 任务分解 | ✅ 自动 | 根据关键词预定义分解策略 |
| 动态 Agent 创建 | ✅ 自动 | 按任务类型创建专业 Agent |
| 并行执行 | ✅ 自动 | 多线程并行处理 |
| 依赖调度 | ✅ 自动 | 按依赖关系正确执行 |
| 结果聚合 | ✅ 自动 | 自动汇总结果 |
| 复杂度分析 | ✅ 自动 | 自动评估任务复杂度 |

### 🔄 使用方式

#### 方式 1: 显式调用（当前）
```bash
jwcode> advanced swarm "重构所有 API 为异步模式"
```

#### 方式 2: 自动检测（新功能）
```bash
# 开启自动模式
jwcode> advanced auto

# 现在输入任务，系统会自动判断是否使用 Swarm
jwcode> refactor all API calls to async  # 自动使用 Swarm
jwcode> hello                             # 使用普通模式
```

#### 方式 3: 先分析再决定
```bash
jwcode> advanced analyze "实现用户认证功能"
# 显示复杂度分析和建议
```

## 任务复杂度评估

系统自动评估以下维度：

### 复杂度评分标准

| 特征 | 加分 | 示例 |
|------|------|------|
| refactor/migrate | +2 | "重构代码" |
| implement feature | +1 | "实现功能" |
| all/multiple | +2 | "所有文件" |
| project | +1 | "整个项目" |
| 描述长度>100 | +1 | 详细描述 |

### 阈值设置

- **复杂度 >= 3**: 建议使用 Agent Swarm
- **复杂度 < 3**: 使用普通单 Agent 模式

## 任务分解策略

### 重构任务 (refactor)
自动分解为：
1. `analyze-code` - 分析代码结构
2. `identify-issues` - 识别问题
3. `plan-refactor` - 制定计划
4. `execute-refactor` - 执行重构
5. `verify-refactor` - 验证结果

### 功能开发 (feature)
自动分解为：
1. `analyze-requirements` - 分析需求
2. `design-architecture` - 设计架构
3. `implement-core` - 实现核心
4. `implement-ui` - 实现界面
5. `write-tests` - 编写测试
6. `integration-test` - 集成测试

### 通用任务
自动分解为：
1. `understand-task` - 理解任务
2. `gather-info` - 收集信息
3. `execute` - 执行
4. `verify` - 验证

## 性能指标

### 测试数据

| 任务类型 | 子任务 | 耗时 | 加速比 |
|----------|--------|------|--------|
| 重构 | 5 | 1241ms | **2.0x** |
| 功能开发 | 6 | 923ms | **3.3x** |

### 与 Kimi Code 对比

| 指标 | Kimi Code | JwCode | 状态 |
|------|-----------|--------|------|
| 最大 Agents | 100 | 50 | 🟡 可配置 |
| 加速比 | 4.5x | 2-3x | 🟡 接近 |
| 自动触发 | ✅ | ✅ | 🟢 已支持 |
| 智能分解 | ✅ AI | ✅ 规则 | 🟡 可改进 |

## 未来改进方向

### 短期 (已实现)
- [x] 基础并行执行
- [x] 任务依赖调度
- [x] 复杂度自动评估
- [x] 自动触发模式

### 中期 (计划)
- [ ] 基于 AI 的智能任务分解
- [ ] 动态调整并行度
- [ ] 学习历史任务模式
- [ ] 支持更多任务类型

### 长期 (目标)
- [ ] 真正的自适应 Agent 创建
- [ ] 跨项目知识共享
- [ ] 预测性任务分解
- [ ] 达到 4.5x 加速比

## 使用建议

### 适合使用 Agent Swarm 的场景
1. 重构涉及多个文件
2. 实现复杂功能
3. 批量修改代码
4. 项目级别分析
5. 多步骤自动化任务

### 不适合使用 Agent Swarm 的场景
1. 简单的问答
2. 单行代码修改
3. 快速查询
4. 互动式对话

## 命令速查

```bash
# 基础命令
advanced thinking     # 切换深度推理模式
advanced yolo         # 切换全自动模式
advanced index        # 项目索引
advanced compact      # 压缩上下文
advanced swarm <task> # 显式使用 Agent Swarm
advanced auto         # 切换自动 Swarm 模式
advanced analyze <task> # 分析任务复杂度
advanced status       # 查看所有状态
```

## 总结

JwCode 的 Agent Swarm 已实现核心功能：
- ✅ 自动任务分解
- ✅ 动态 Agent 创建
- ✅ 并行执行
- ✅ 依赖调度
- ✅ 结果聚合
- ✅ 复杂度分析
- ✅ 自动触发

当前实现基于规则匹配，未来可升级为 AI 驱动的智能分解。
