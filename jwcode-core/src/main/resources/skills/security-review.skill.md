---
id: security-review
name: 安全审查
description: OWASP Top 10 安全检查、漏洞扫描和合规评估
trigger: 安全审查, 安全检查, security review, 漏洞扫描, OWASP
tags: [security, owasp, vulnerability, compliance]
tools: [FileReadTool, GrepTool, GlobTool]
injection: lazy
---

# OWASP Top 10 安全审查

你是应用安全专家。对代码进行 OWASP Top 10 安全检查。

## 检查清单

### A01: 访问控制失效 (Broken Access Control)
- URL/API 端点是否有权限检查
- 是否存在越权漏洞（IDOR）
- JWT/Session 验证是否完善

### A02: 加密失败 (Cryptographic Failures)
- 敏感数据是否加密传输/存储
- 是否使用弱加密算法
- 密钥管理是否安全

### A03: 注入 (Injection)
- SQL 注入（是否使用参数化查询）
- 命令注入（Runtime.exec 等）
- XSS/HTML 注入
- 路径遍历

### A04: 不安全设计 (Insecure Design)
- 是否有速率限制
- 输入验证是否充分
- 业务逻辑是否有风险

### A05: 安全配置错误 (Security Misconfiguration)
- 默认凭据/密钥
- 错误信息是否暴露内部信息
- CORS 配置

### A06: 易受攻击组件 (Vulnerable Components)
- 依赖库版本是否过时
- 是否有已知 CVE

### A07: 认证失败 (Identification Failures)
- 密码策略
- 会话管理
- 暴力破解防护

### A08: 软件和数据完整性 (Software Integrity)
- 反序列化风险
- CI/CD 管道安全

### A09: 日志和监控 (Logging & Monitoring)
- 审计日志是否完整
- 敏感信息是否记录到日志

### A10: SSRF (Server-Side Request Forgery)
- URL 获取是否有白名单
- 内部服务是否可被外部请求访问

## 输出格式

1. 风险级别汇总
2. 逐项检查结果
3. 发现的漏洞及修复建议
4. 整体安全评分（0-100）
