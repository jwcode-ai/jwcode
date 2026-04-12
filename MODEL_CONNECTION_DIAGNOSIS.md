# JwCode 模型连接问题诊断报告

## 问题现象
```
模型: kimi-for-coding
状态: Connection failed: request timed out
```

## 根本原因

### 1. API 端点配置错误 ❌

你的配置文件 (`~/.jwcode/config.properties`)：
```properties
api.endpoint=https://api.kimi.com/coding  # ❌ 错误！
api.model=kimi-for-coding
api.timeout=3000                          # ❌ 太短！
```

**问题分析：**
- 你配置的是 `https://api.kimi.com/coding`
- 但正确的 Kimi API 端点是 `https://kimi.com/coding`
- 错误的端点返回 404，导致请求超时

### 2. 超时时间设置过短 ❌

`api.timeout=3000` (3秒) 对于 API 调用来说太短了，建议至少 30 秒。

---

## 诊断测试结果

| 测试项目 | 结果 | 说明 |
|---------|------|------|
| 基础网络连通性 | ✅ 正常 | 可以访问互联网 |
| DNS 解析 | ✅ 正常 | api.minimaxi.com 解析正常 |
| TCP 连接 | ✅ 正常 | 443 端口可连接 |
| HTTPS 连接 (MiniMax) | ✅ 正常 | 返回 404 (端点存在) |
| API 调用 (MiniMax) | ✅ 正常 | 返回 401 (需要 API Key) |
| HTTPS 连接 (Kimi 正确) | ✅ 正常 | 返回 302 (重定向，正常) |
| HTTPS 连接 (Kimi 错误配置) | ❌ 失败 | 返回 404 (你的配置) |
| 代理检测 | ✅ 正常 | 未使用代理 |

---

## 解决方案

### 方案一：使用正确的 Kimi 端点 (推荐)

在 JwCode CLI 中执行：
```bash
config set-endpoint https://kimi.com/coding
config set-timeout 30000
```

或手动修改配置文件 `~/.jwcode/config.properties`：
```properties
api.key=sk-kimi-7gEc2ksL3JyAY6ogIrnAFTp6vrMHOjLwtDPgl6yLIPSjSKuw1BwclXdxGjZ0z0no
api.endpoint=https://kimi.com/coding
api.model=kimi-for-coding
api.timeout=30000
```

### 方案二：切换到 MiniMax 端点

如果你的 Kimi API Key 也是 MiniMax 兼容的，可以：
```bash
config set-endpoint https://api.minimaxi.com/anthropic
config set-model MiniMax-M1
config set-timeout 30000
```

### 方案三：增加超时时间

如果网络较慢，可以增加超时时间：
```bash
config set-timeout 60000  # 60秒
```

---

## 验证修复

修改配置后，在 JwCode CLI 中运行：
```bash
config show  # 查看当前配置
status       # 检查模型状态
```

或者运行单元测试验证：
```bash
mvn test -Dtest=ModelConnectionDiagnosticTest -pl jwcode-core
```

---

## 总结

| 配置项 | 你的配置 | 正确配置 |
|-------|---------|---------|
| 端点 | `https://api.kimi.com/coding` ❌ | `https://kimi.com/coding` ✅ |
| 超时 | `3000` (3秒) ❌ | `30000` (30秒) ✅ |
| 模型 | `kimi-for-coding` ✅ | `kimi-for-coding` ✅ |

**问题根源：** `api.kimi.com` 这个域名不存在或端点不正确，导致 HTTP 404，然后连接超时。

**修复方法：** 将端点改为 `https://kimi.com/coding`，并将超时时间增加到 30000ms。
