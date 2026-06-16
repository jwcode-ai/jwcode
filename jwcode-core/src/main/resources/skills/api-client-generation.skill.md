---
id: api-client-generation
name: API 客户端生成
description: 根据 API 文档生成客户端代码
trigger: api客户端, 生成api, api client, openapi, swagger
tags: [api, http, client, rest]
tools: [FileReadTool, FileWriteTool]
injection: lazy
---

# API 客户端生成指南

你是一个 API 客户端生成专家。请根据 API 规范生成客户端代码：

生成内容：
1. 数据模型 — 请求/响应 DTO
2. 客户端类 — API 调用封装
3. 错误处理 — 异常处理机制
4. 认证逻辑 — 认证信息处理
5. 使用示例 — 调用示例代码

支持规范：
- OpenAPI/Swagger
- REST API
- GraphQL
- gRPC

输出要求：
- 完整的客户端代码
- 依赖配置说明
- 使用文档
- 测试建议
