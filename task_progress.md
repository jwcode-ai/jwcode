# 实施任务进度

## Phase 1: TodoWrite 工具增强
- [x] 1.1 增强 TodoItem 支持 content/activeForm 双形式
- [x] 1.2 增强 TodoWriteTool 严格状态机纪律（exactly one in_progress）
- [ ] 1.3 创建 TodoWriteBroadcaster（WebSocket 广播）
- [ ] 1.4 前端 TodoWrite 实时显示组件

## Phase 2: Plan Mode 权限隔离
- [x] 2.1 创建 PlanModeManager（模式状态管理 + 工具白名单）
- [x] 2.2 增强 EnterPlanModeTool（完整流程）
- [x] 2.3 增强 ExitPlanModeV2Tool（完整流程）
- [ ] 2.4 集成到 ToolExecutor（权限检查）
- [ ] 2.5 前端 Plan/Act 模式切换 UI 增强
