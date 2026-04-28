package com.jwcode.core.service.structured;

/**
 * 结构化上下文管理器使用示例
 * 
 * 演示如何使用 StructuredContextManager 进行上下文管理
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class StructuredContextDemo {
    
    public static void main(String[] args) {
        System.out.println("============ 结构化上下文管理器演示 ============\n");
        
        // 1. 创建管理器
        StructuredContextManager manager = new StructuredContextManager(
            "demo-session",  // sessionId
            10,             // maxActiveSize - 超过10条消息时触发评估
            3,              // minRetainCount - 最少保留3条
            true,           // enableArchive - 启用归档
            5               // periodicEvalInterval - 每5条消息评估一次
        );
        
        System.out.println("1. 创建管理器成功");
        System.out.println(manager.generateReport());
        
        // 2. 添加用户意图消息
        StructuredMessage msg1 = MessageFactory.getInstance().createIntent("帮我实现登录功能");
        manager.addMessage(msg1);
        System.out.println("2. 添加用户意图: " + msg1.getId());
        
        // 3. 添加 AI 回复
        StructuredMessage msg2 = MessageFactory.getInstance().createResponse(
            "好的，我来帮你实现登录功能。首先需要创建用户表和相应的Service...",
            msg1.getId()
        );
        manager.addMessage(msg2);
        System.out.println("3. 添加AI回复: " + msg2.getId());
        
        // 4. 添加工具结果
        StructuredMessage msg3 = MessageFactory.getInstance().createToolResult(
            "UserService",
            true,
            "tool_001",
            "创建成功，文件路径: src/.../UserService.java"
        );
        manager.addMessage(msg3);
        System.out.println("4. 添加工具结果: " + msg3.getId());
        
        // 5. 添加更多消息（模拟对话）
        for (int i = 0; i < 8; i++) {
            String content = "这是第 " + (i + 5) + " 条消息的内容...";
            MessageType type = (i % 3 == 0) ? MessageType.ANSWER : MessageType.INTERIM_DISCUSSION;
            MessageMetadata meta = new MessageMetadata(type);
            StructuredMessage msg = new StructuredMessage("assistant", content, meta);
            manager.addMessage(msg);
        }
        
        System.out.println("\n5. 添加了更多消息后:");
        System.out.println(manager.generateReport());
        
        // 6. 手动触发评估
        System.out.println("\n6. 手动触发评估:");
        EvaluationResult result = manager.triggerEvaluation(EvaluationTrigger.MANUAL);
        if (result != null) {
            System.out.println("评估结果: " + result.getSummary());
            System.out.println("  - 保留: " + result.getRetainMessageIds().size() + " 条");
            System.out.println("  - 丢弃: " + result.getDropMessageIds().size() + " 条");
        }
        
        System.out.println("\n7. 评估后状态:");
        System.out.println(manager.generateReport());
        
        // 7. 查看归档
        System.out.println("\n8. 归档消息数: " + manager.getArchiveStore().size());
        
        // 8. 恢复消息
        if (!manager.getArchiveStore().isEmpty()) {
            String firstArchivedId = manager.getArchiveStore().keySet().iterator().next();
            System.out.println("\n9. 尝试恢复消息: " + firstArchivedId);
            StructuredMessage restored = manager.restoreMessage(firstArchivedId);
            if (restored != null) {
                System.out.println("恢复成功: " + restored.getId());
            }
        }
        
        System.out.println("\n10. 最终状态:");
        System.out.println(manager.generateReport());
        
        System.out.println("\n============ 演示完成 ============");
    }
}