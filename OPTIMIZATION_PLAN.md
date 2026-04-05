# JwCode 项目优化计划

## 目标
参照 Claude Code (JavaScript) 项目，全面优化 jwcode 项目，实现功能对等。

## 现状对比分析

### JavaScript 项目 (claude-code-source-main)
- **命令系统**: 86+ 个命令
- **工具系统**: 40+ 个工具
- **核心模块**: QueryEngine, ToolSystem, PermissionSystem, ConfigSystem, etc.
- **高级功能**: 桥接模式、语音、Vim模式、主题系统、插件系统、MCP支持等

### Java 项目 (jwcode)
- **工具系统**: 71 个工具相关文件 (基础实现)
- **服务层**: 14 个服务
- **命令系统**: ❌ 缺失
- **高级功能**: 部分实现

## 优化阶段

### 阶段 1: 核心架构优化 (高优先级)

#### 1.1 完善工具系统
| 工具 | 现状 | 目标 |
|------|------|------|
| BashTool | ✅ 基本实现 | 添加沙箱支持、安全验证 |
| FileReadTool | ✅ 基本实现 | 添加图片/文档支持 |
| FileEditTool | ✅ 基本实现 | 添加 diff 验证 |
| FileWriteTool | ✅ 基本实现 | 添加自动创建目录 |
| GlobTool | ✅ 基本实现 | 优化性能 |
| GrepTool | ✅ 基本实现 | 添加正则支持 |
| WebSearchTool | ⚠️ 骨架实现 | 实现真实搜索 |
| WebFetchTool | ⚠️ 骨架实现 | 实现真实抓取 |
| TodoWriteTool | ✅ 基本实现 | 优化存储 |
| AgentTool | ⚠️ 骨架实现 | 实现多Agent |
| Task*Tool | ✅ 基本实现 | 完善任务系统 |
| MCPTool | ⚠️ 骨架实现 | 实现MCP协议 |

#### 1.2 修复关键 Bug
- [x] ToolResult 序列化问题 (已完成)
- [x] getInputType/getOutputType 缺失 (已完成)
- [ ] 工具参数双重转义问题
- [ ] 会话消息持久化

### 阶段 2: 命令系统实现 (高优先级)

参照 JavaScript 项目，实现核心命令：

```
/commands
  /config       - 配置管理
  /help         - 帮助系统
  /exit         - 退出命令
  /clear        - 清除会话
  /cost         - 成本追踪
  /usage        - 使用情况
  /model        - 模型切换
  /theme        - 主题切换
  /tools        - 工具管理
  /status       - 状态查看
  /compact      - 会话压缩
  /resume       - 恢复会话
  /rename       - 重命名会话
  /share        - 分享会话
  /export       - 导出会话
  /hooks        - Git hooks
  /mcp          - MCP管理
  /tasks        - 任务管理
  /skills       - 技能管理
  /agents       - Agent管理
```

### 阶段 3: 高级功能实现 (中优先级)

#### 3.1 权限系统完善
- 文件系统权限 (只读/读写/删除)
- 命令执行权限 (安全命令/危险命令)
- 网络请求权限
- 用户确认流程

#### 3.2 配置系统增强
- 用户级配置 (~/.jwcode/config.json)
- 项目级配置 (.jwcode/config.json)
- 环境变量支持
- 配置热重载

#### 3.3 会话管理
- 会话持久化
- 会话压缩 (token 限制处理)
- 会话恢复
- 多会话支持

#### 3.4 成本追踪
- API 调用统计
- Token 使用量
- 成本估算
- 预算限制

### 阶段 4: UI/UX 优化 (中优先级)

- 彩色输出支持
- 进度指示器
- 交互式确认
- 主题系统 (深色/浅色)
- 语法高亮

### 阶段 5: 扩展功能 (低优先级)

- 插件系统
- 桥接模式 (远程执行)
- Vim 模式
- 语音支持
- GitHub 集成

## 技术债务清理

1. **代码规范**: 统一代码风格
2. **测试覆盖**: 添加单元测试
3. **文档完善**: API 文档、用户手册
4. **错误处理**: 统一异常处理
5. **日志系统**: 结构化日志

## 实施建议

### 立即执行 (本周)
1. 修复工具参数转义问题
2. 实现核心命令系统框架
3. 完善 WebSearch/WebFetch 工具

### 短期目标 (本月)
1. 完成所有工具的真实实现
2. 实现 20+ 核心命令
3. 完善权限系统

### 中期目标 (3个月)
1. 功能对齐 JavaScript 项目核心功能
2. 完善的测试覆盖
3. 用户文档

### 长期目标 (6个月)
1. 高级功能 (插件、桥接等)
2. 性能优化
3. 社区生态

## 文件变更清单

### 新增文件
- `jwcode-core/src/main/java/com/jwcode/core/command/` - 命令系统
- `jwcode-core/src/main/java/com/jwcode/core/permission/` - 权限系统
- `jwcode-core/src/main/java/com/jwcode/core/config/` - 配置系统
- `jwcode-core/src/main/java/com/jwcode/core/session/` - 会话管理

### 修改文件
- `BashTool.java` - 添加沙箱支持
- `WebSearchTool.java` - 实现真实搜索
- `WebFetchTool.java` - 实现真实抓取
- `AgentTool.java` - 实现多Agent
- `QueryEngine.java` - 优化对话循环
- `ApiClient.java` - 修复序列化

## 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 工作量大 | 高 | 分阶段实施 |
| API 兼容性 | 中 | 保持向后兼容 |
| 性能问题 | 中 | 持续性能测试 |
| 测试不足 | 高 | 增加自动化测试 |
