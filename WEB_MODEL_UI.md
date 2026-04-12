# Web 模型状态展示功能

## 功能概述

在 Web 界面中添加了模型池状态展示页面，可以实时查看：
- 模型池整体健康状态
- 各个模型实例的详细信息
- 实时统计信息（成功率、延迟、请求数等）
- 模型启停控制

## API 接口

### 获取模型池状态
```
GET /api/models/status
```

响应示例：
```json
{
  "success": true,
  "data": {
    "totalInstances": 2,
    "availableInstances": 2,
    "healthyInstances": 2,
    "totalRequests": 1000,
    "loadBalanceStrategy": "ADAPTIVE",
    "healthRate": 100,
    "overallStatus": "healthy"
  }
}
```

### 获取所有模型列表
```
GET /api/models
```

响应示例：
```json
{
  "success": true,
  "data": [
    {
      "id": "primary",
      "name": "MiniMax-M2.7",
      "provider": "minimax",
      "providerDisplay": "MiniMax API",
      "healthStatus": "up",
      "isHealthy": true,
      "isAvailable": true,
      "successRate": 98,
      "avgLatencyMs": 150,
      "totalRequests": 500,
      "score": 0.95
    }
  ],
  "total": 1
}
```

### 获取单个模型详情
```
GET /api/models/{modelId}
```

### 刷新模型健康状态
```
POST /api/models/{modelId}/refresh
```

### 启用/停用模型
```
POST /api/models/{modelId}/enable
POST /api/models/{modelId}/disable
```

## Web 界面

### 访问方式
1. 打开 Web UI: http://localhost:8080
2. 点击顶部导航栏的 "🧠 模型" 按钮
3. 或点击左侧菜单的 "🧠 模型状态"

### 展示内容

#### 整体状态卡片
- **整体状态**: 健康/降级/异常
- **实例数量**: 健康实例数/总实例数
- **负载策略**: 当前使用的负载均衡策略
- **健康度**: 百分比显示

#### 模型实例卡片
每个模型显示：
- 模型名称和提供商
- 健康状态图标（✓/✗/⚠/⟳）
- 评分（综合评分 0-1）
- 成功率、平均延迟
- 当前连接数/最大并发
- 权重、总请求数
- 运行时间
- 操作按钮（刷新、启用/停用）

## 代码实现

### 后端文件
- `jwcode-web/src/main/java/com/jwcode/web/ModelInfoHandler.java` - API 处理器
- `jwcode-web/src/main/java/com/jwcode/web/WebServer.java` - 注册路由和设置模型池

### 前端代码
在 `WebServer.java` 的 HTML 中添加了：
- 模型状态视图 (`models-view`)
- `loadModels()` - 加载模型数据
- `renderModelsStatus()` - 渲染整体状态
- `renderModelsList()` - 渲染模型列表
- `refreshModel()` / `toggleModel()` - 操作函数

### 设置模型池

```java
WebServer server = new WebServer(8080);
server.setModelPool(modelPool);  // 设置模型池以启用状态展示
server.start();
```

## 界面截图预览

```
┌─────────────────────────────────────────────────────────────┐
│  JwCode Web              [🧠 模型] [🛠️ 工具] [⚙️ 设置]      │
├──────────────┬──────────────────────────────────────────────┤
│              │  🧠 模型状态                     [🔄 刷新]  │
│  🧠 模型状态  │                                              │
│  🛠️ 工具列表  │  ┌─────────────┐ ┌─────────────┐            │
│  🎯 技能管理  │  │ 整体状态    │ │ 实例数量    │            │
│  🤖 Agent切换 │  │ ✓ 健康      │ │ 2/2         │            │
│  📋 计划模板  │  │ 100% 健康度 │ │ 健康/总计   │            │
│  ⚙️ 配置设置  │  └─────────────┘ └─────────────┘            │
│              │                                              │
│  ─────────── │  ┌─────────────────────────────────────┐    │
│              │  │ ✓ MiniMax-M2.7                      │    │
│  ⚡ JwCode   │  │ MiniMax API                         │    │
│  v1.0.0      │  │ 评分: 0.95                          │    │
│              │  │ ┌──────────┐┌──────────┐            │    │
│              │  │ │ 成功率   ││ 平均延迟 │            │    │
│              │  │ │ 98%      ││ 150ms    │            │    │
│              │  │ └──────────┘└──────────┘            │    │
│              │  │ [🔄 刷新] [⏸ 停用]                 │    │
│              │  └─────────────────────────────────────┘    │
└──────────────┴──────────────────────────────────────────────┘
```

## 后续扩展

1. **实时更新**: 使用 WebSocket 推送状态变化
2. **历史图表**: 展示成功率、延迟的历史趋势
3. **模型对比**: 对比不同模型的性能指标
4. **智能建议**: 根据健康状态给出优化建议
