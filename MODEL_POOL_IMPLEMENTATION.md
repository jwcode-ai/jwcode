# 模型池化架构实现总结

## 问题修复

### 原始问题
- **错误**: `HTTP 400 [invalid_request_error]: Invalid request Error`
- **原因**: `ApiClient.testModel()` 始终使用 Anthropic 格式构建请求体，不考虑实际的 API 类型
- **场景**: 用户使用 `kimi-for-coding` 模型但配置了错误的 API 端点

### 解决方案
创建了全新的模型池化架构，核心改进包括：

1. **智能提供商检测**: 根据 URL 自动识别 API 提供商类型
2. **模型兼容性检查**: 在发送请求前验证模型名称与 API 类型的兼容性
3. **正确的请求格式**: 根据提供商类型选择正确的请求体格式
4. **清晰的错误提示**: 当模型与端点不匹配时，提供具体的修复建议

---

## 架构设计

### 核心组件

```
┌─────────────────────────────────────────────────────────────┐
│                    ModelPoolManager                         │
│              (负载均衡、健康检查、故障转移)                    │
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                           │
│  ModelId, ProviderType, HealthStatus, ModelCapability      │
│  ModelStats, ModelConfig, ModelInstance                     │
├─────────────────────────────────────────────────────────────┤
│                 Infrastructure Layer                        │
│  ApiClientV2, ModelLoader, LoadBalanceStrategy             │
└─────────────────────────────────────────────────────────────┘
```

### 新创建的文件

| 文件 | 说明 |
|------|------|
| `ModelId.java` | 模型实例唯一标识符（值对象） |
| `ProviderType.java` | API 提供商类型枚举，支持自动检测 |
| `HealthStatus.java` | 健康状态（值对象） |
| `ModelCapability.java` | 模型能力描述 |
| `ModelStats.java` | 运行时统计信息 |
| `ModelConfig.java` | 模型配置（值对象） |
| `ModelInstance.java` | 模型实例（领域实体） |
| `LoadBalanceStrategy.java` | 负载均衡策略（轮询、加权、最少连接等） |
| `ModelPool.java` | 模型池管理器 |
| `ModelPoolConfig.java` | 模型池配置 |
| `ModelLoader.java` | 模型加载器接口 |
| `ApiClientV2.java` | 改进版 API 客户端 |

---

## 负载均衡策略

支持多种负载均衡算法：

1. **ROUND_ROBIN** - 轮询
2. **RANDOM** - 随机
3. **WEIGHTED_RANDOM** - 加权随机
4. **LEAST_CONNECTIONS** - 最少连接
5. **BEST_SCORE** - 最佳评分
6. **ADAPTIVE** - 自适应（默认）

---

## 使用方式

### 配置示例

```properties
# 单模型配置（向后兼容）
api.endpoint=https://api.minimaxi.com/anthropic
api.key=your-api-key
api.model=MiniMax-M2.7
```

### 代码中使用

```java
// 创建 QueryEngine（自动使用新的 ApiClientV2）
QueryEngine queryEngine = QueryEngine.builder()
    .session(session)
    .model("MiniMax-M2.7")
    .build();

// 发送请求
CompletableFuture<QueryResult> result = queryEngine.query("Hello");
```

---

## 错误处理改进

### 模型与端点不匹配时

```
===============================================================
| 模型与 API 端点不兼容                                         |
===============================================================
| 模型: kimi-for-coding
| 当前提供商: Anthropic API
|                                                              |
| 建议操作:                                                    |
| 1. 切换到 Kimi API 端点:                                     |
|    config set-endpoint https://kimi.com/coding               |
| 2. 或者切换为当前提供商支持的模型:                           |
|    model <supported-model-name>                              |
===============================================================
```

---

## 测试结果

```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ModelIdCreation ✓
[INFO] ProviderTypeDetection ✓
[INFO] HealthStatus ✓
[INFO] ModelCapability ✓
[INFO] ModelConfigBuilder ✓
[INFO] ModelInstance ✓
[INFO] ModelStats ✓
[INFO] LoadBalanceStrategies ✓
[INFO] ModelPoolCreation ✓
[INFO] ModelCompatibility ✓
```

---

## 向后兼容性

- 完全兼容现有配置文件格式
- `ApiClientV2` 可以无缝替换 `ApiClient`
- 单模型配置自动升级为单实例池

---

## 后续扩展建议

1. **多模型配置**: 支持配置多个模型实例，实现故障转移
2. **动态权重调整**: 根据实时性能动态调整实例权重
3. **模型预热**: 新实例加入时自动预热
4. **指标监控**: 集成 Micrometer 导出 Prometheus 指标
5. **响应式升级**: 使用 Project Reactor 实现完全非阻塞
