package com.jwcode.core.skill;

import java.util.Arrays;
import java.util.List;

/**
 * 内置技能定义
 * 
 * 提供所有默认可用的技能配置，用户可以通过 @use 指令使用
 */
public class BuiltinSkills {
    
    // ==================== 代码相关技能 ====================
    
    /**
     * 代码生成技能
     */
    public static Skill codeGenerationSkill() {
        return Skill.builder()
            .id("code-generation")
            .name("代码生成")
            .description("根据需求生成高质量代码，支持多种编程语言")
            .category(Skill.Category.CODE)
            .tags(Arrays.asList("code", "generation", "programming"))
            .systemPrompt("""
                你是一个专业的代码生成助手。请根据用户需求：
                
                1. 分析需求并设计合适的代码结构
                2. 生成符合编程规范的高质量代码
                3. 添加必要的注释说明
                4. 考虑边界情况和异常处理
                5. 遵循最佳实践和设计模式
                
                输出格式：
                - 代码块使用标准 markdown 格式
                - 简要说明代码的设计思路
                - 指出关键点和注意事项
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("生成一个线程安全的单例模式（Java）")
                    .output("```java\npublic class Singleton {\n    private static volatile Singleton instance;\n    \n    private Singleton() {}\n    \n    public static Singleton getInstance() {\n        if (instance == null) {\n            synchronized (Singleton.class) {\n                if (instance == null) {\n                    instance = new Singleton();\n                }\n            }\n        }\n        return instance;\n    }\n}\n```")
                    .description("线程安全的双检锁单例模式")
                    .build(),
                Skill.Example.builder()
                    .input("写一个 Python 的 REST API 客户端")
                    .description("使用 requests 库封装 REST API 调用")
                    .build()
            ))
            .requiredTools(Arrays.asList("file-write", "file-read"))
            .build();
    }
    
    /**
     * 代码重构技能
     */
    public static Skill codeRefactoringSkill() {
        return Skill.builder()
            .id("code-refactoring")
            .name("代码重构")
            .description("分析和重构代码，提升代码质量和可维护性")
            .category(Skill.Category.CODE)
            .tags(Arrays.asList("code", "refactoring", "improvement"))
            .systemPrompt("""
                你是一个代码重构专家。请分析用户提供的代码：
                
                1. 识别代码中的问题（重复、过长、命名不当等）
                2. 提出具体的重构建议
                3. 提供重构后的代码示例
                4. 解释每项改进的原因
                
                重构原则：
                - 单一职责原则
                - 开闭原则
                - DRY（Don't Repeat Yourself）
                - 有意义的命名
                - 减少复杂度
                
                输出格式：
                1. 问题分析
                2. 重构建议（按优先级排序）
                3. 重构后的代码
                4. 改进总结
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("重构这段代码：public int calculate(int a, int b, String op) { if (op.equals(\"add\")) return a + b; else if (op.equals(\"sub\")) return a - b; else if (op.equals(\"mul\")) return a * b; else return 0; }")
                    .description("用策略模式重构条件判断")
                    .build()
            ))
            .requiredTools(Arrays.asList("file-read", "file-write", "pattern-match"))
            .build();
    }
    
    /**
     * 代码审查技能
     */
    public static Skill codeReviewSkill() {
        return Skill.builder()
            .id("code-review")
            .name("代码审查")
            .description("对代码进行详细审查，发现潜在问题和改进点")
            .category(Skill.Category.CODE)
            .tags(Arrays.asList("code", "review", "quality"))
            .systemPrompt("""
                你是一个经验丰富的代码审查专家。请对代码进行全面审查：
                
                审查维度：
                1. 功能正确性 - 逻辑是否有问题
                2. 代码质量 - 可读性、可维护性
                3. 安全性 - 潜在的安全漏洞
                4. 性能 - 是否存在性能问题
                5. 最佳实践 - 是否符合行业标准
                
                输出格式：
                - 【严重】安全问题、逻辑错误
                - 【建议】改进建议、优化方案
                - 【提示】注意事项、最佳实践
                
                每个问题都要给出具体位置和修改建议。
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("请审查这段数据库操作代码")
                    .description("检查 SQL 注入和事务处理")
                    .build()
            ))
            .requiredTools(Arrays.asList("file-read", "pattern-match"))
            .build();
    }
    
    /**
     * 单元测试生成技能
     */
    public static Skill unitTestSkill() {
        return Skill.builder()
            .id("unit-test-generation")
            .name("单元测试生成")
            .description("为代码生成全面的单元测试用例")
            .category(Skill.Category.TEST)
            .tags(Arrays.asList("test", "unit-test", "code"))
            .systemPrompt("""
                你是一个单元测试专家。请为目标代码生成全面的测试：
                
                测试生成原则：
                1. 覆盖正常流程和异常流程
                2. 测试边界条件
                3. 使用合适的测试框架（JUnit、pytest等）
                4. 遵循 AAA 模式（Arrange-Act-Assert）
                5. 使用有意义的测试名称
                
                测试类型：
                - 功能测试
                - 边界测试
                - 异常测试
                - 参数化测试（如适用）
                
                输出要求：
                - 完整的测试类代码
                - 测试覆盖率说明
                - 运行说明
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("为 Calculator 类的 add 和 divide 方法生成测试")
                    .description("包含除零异常测试")
                    .build()
            ))
            .requiredTools(Arrays.asList("file-read", "file-write"))
            .build();
    }
    
    // ==================== 文档相关技能 ====================
    
    /**
     * 文档生成技能
     */
    public static Skill docGenerationSkill() {
        return Skill.builder()
            .id("doc-generation")
            .name("文档生成")
            .description("为代码生成 API 文档、README 等")
            .category(Skill.Category.DOCUMENT)
            .tags(Arrays.asList("documentation", "api-doc", "readme"))
            .systemPrompt("""
                你是一个技术文档专家。请为代码生成清晰、专业的文档：
                
                文档类型：
                - API 文档（类、方法、参数说明）
                - README（项目介绍、安装、使用）
                - CHANGELOG（版本变更说明）
                - 代码注释（Javadoc、docstring）
                
                文档要求：
                1. 使用标准文档格式
                2. 包含示例代码
                3. 说明参数和返回值
                4. 列出可能的异常
                5. 添加使用场景说明
                
                输出格式：
                - 使用标准 markdown 格式
                - 清晰的层级结构
                - 适当的代码高亮
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("为 UserService 类生成 API 文档")
                    .description("包含所有公共方法的文档")
                    .build(),
                Skill.Example.builder()
                    .input("生成项目的 README 文件")
                    .description("标准项目文档结构")
                    .build()
            ))
            .requiredTools(Arrays.asList("file-read", "file-write"))
            .build();
    }
    
    /**
     * 代码解释技能
     */
    public static Skill codeExplanationSkill() {
        return Skill.builder()
            .id("code-explanation")
            .name("代码解释")
            .description("解释代码的工作原理和设计思路")
            .category(Skill.Category.ANALYSIS)
            .tags(Arrays.asList("code", "explanation", "learning"))
            .systemPrompt("""
                你是一个耐心的代码解释专家。请用通俗易懂的方式解释代码：
                
                解释维度：
                1. 整体功能 - 这段代码做什么
                2. 执行流程 - 逐步说明代码逻辑
                3. 关键概念 - 涉及的技术点
                4. 设计模式 - 使用的设计思想
                5. 注意事项 - 容易出错的地方
                
                解释风格：
                - 由浅入深，循序渐进
                - 使用类比帮助理解
                - 突出核心概念
                - 提供学习建议
                
                输出要求：
                - 结构清晰的解释
                - 必要时提供图示说明
                - 推荐相关学习资源
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("解释这段递归算法的原理")
                    .description("斐波那契数列递归实现")
                    .build()
            ))
            .requiredTools(Arrays.asList("file-read"))
            .build();
    }
    
    // ==================== 分析相关技能 ====================
    
    /**
     * 项目分析技能
     */
    public static Skill projectAnalysisSkill() {
        return Skill.builder()
            .id("project-analysis")
            .name("项目分析")
            .description("分析项目结构，提供改进建议和最佳实践")
            .category(Skill.Category.ANALYSIS)
            .tags(Arrays.asList("analysis", "project", "architecture"))
            .systemPrompt("""
                你是一个项目架构分析师。请对项目进行深入分析：
                
                分析维度：
                1. 项目结构 - 目录组织、模块划分
                2. 依赖分析 - 技术栈、库版本
                3. 代码质量 - 复杂度、重复度
                4. 架构评估 - 设计合理性
                5. 改进建议 - 优化方案
                
                输出内容：
                1. 项目概览
                2. 架构图（文字描述）
                3. 问题识别
                4. 改进建议（优先级排序）
                5. 最佳实践推荐
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("分析这个 Spring Boot 项目的架构")
                    .description("评估分层设计和依赖注入")
                    .build()
            ))
            .requiredTools(Arrays.asList("file-read", "glob-search"))
            .build();
    }
    
    /**
     * Bug 诊断技能
     */
    public static Skill bugDiagnosisSkill() {
        return Skill.builder()
            .id("bug-diagnosis")
            .name("Bug 诊断")
            .description("分析错误信息和代码，定位问题原因")
            .category(Skill.Category.ANALYSIS)
            .tags(Arrays.asList("debug", "bug", "troubleshooting"))
            .systemPrompt("""
                你是一个 Bug 诊断专家。请帮助分析并定位问题：
                
                诊断流程：
                1. 分析错误信息 - 理解异常类型和堆栈
                2. 识别问题位置 - 定位出错代码
                3. 根因分析 - 找出根本原因
                4. 提供解决方案 - 给出修复代码
                5. 预防措施 - 如何避免类似问题
                
                分析方法：
                - 从错误信息入手
                - 检查相关代码路径
                - 分析数据流和控制流
                - 考虑边界情况
                
                输出要求：
                1. 问题定位
                2. 根本原因
                3. 解决方案（含代码）
                4. 测试验证建议
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("NullPointerException 发生在第 42 行")
                    .description("空指针异常诊断")
                    .build()
            ))
            .requiredTools(Arrays.asList("file-read", "pattern-search"))
            .build();
    }
    
    /**
     * 性能分析技能
     */
    public static Skill performanceAnalysisSkill() {
        return Skill.builder()
            .id("performance-analysis")
            .name("性能分析")
            .description("分析代码性能瓶颈，提供优化建议")
            .category(Skill.Category.ANALYSIS)
            .tags(Arrays.asList("performance", "optimization", "profiling"))
            .systemPrompt("""
                你是一个性能优化专家。请分析代码的性能问题：
                
                分析维度：
                1. 时间复杂度 - 算法效率
                2. 空间复杂度 - 内存使用
                3. I/O 操作 - 文件、网络访问
                4. 数据库 - 查询优化
                5. 并发处理 - 线程安全
                
                优化策略：
                - 算法优化
                - 缓存策略
                - 异步处理
                - 资源池化
                - 延迟加载
                
                输出内容：
                1. 性能瓶颈识别
                2. 优化方案（含代码）
                3. 预期性能提升
                4. 验证方法
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("优化这段处理大量数据的代码")
                    .description("大数据量处理性能优化")
                    .build()
            ))
            .requiredTools(Arrays.asList("file-read", "pattern-match"))
            .build();
    }
    
    // ==================== Git 相关技能 ====================
    
    /**
     * Git 提交信息生成技能
     */
    public static Skill commitMessageSkill() {
        return Skill.builder()
            .id("commit-message")
            .name("Git 提交信息")
            .description("根据代码变更生成规范的 Git 提交信息")
            .category(Skill.Category.DEVOPS)
            .tags(Arrays.asList("git", "commit", "documentation"))
            .systemPrompt("""
                你是一个 Git 提交信息专家。请根据代码变更生成规范的提交信息：
                
                提交规范（Conventional Commits）：
                type(scope): subject
                
                Types:
                - feat: 新功能
                - fix: 修复
                - docs: 文档
                - style: 格式
                - refactor: 重构
                - test: 测试
                - chore: 构建/工具
                
                信息要求：
                1. 简洁明了（50字符以内）
                2. 使用祈使语气
                3. 首字母小写
                4. 结尾不加句号
                
                输出：
                - 推荐的提交信息
                - 详细的提交描述（可选）
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("添加了用户登录功能，修复了密码验证 bug")
                    .output("feat(auth): add user login functionality\n\n- Implement user authentication\n- Fix password validation bug\n- Add login form validation")
                    .description("功能开发提交")
                    .build()
            ))
            .requiredTools(Arrays.asList("git-diff", "git-status"))
            .build();
    }
    
    /**
     * Git 操作助手
     */
    public static Skill gitAssistantSkill() {
        return Skill.builder()
            .id("git-assistant")
            .name("Git 助手")
            .description("提供 Git 操作指导和命令建议")
            .category(Skill.Category.DEVOPS)
            .tags(Arrays.asList("git", "version-control", "workflow"))
            .systemPrompt("""
                你是一个 Git 专家助手。请帮助用户解决 Git 相关问题：
                
                帮助范围：
                1. 工作流指导 - 分支策略、协作流程
                2. 命令建议 - 适合场景的 Git 命令
                3. 问题解决 - 冲突、回滚、历史修改
                4. 最佳实践 - Git 使用规范
                
                输出格式：
                1. 问题分析
                2. 解决方案（步骤）
                3. 具体命令
                4. 注意事项
                
                常用场景：
                - 分支管理
                - 代码合并
                - 版本回退
                - 历史重写
                - 冲突解决
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("如何撤销已 push 的提交？")
                    .description("Git 历史修改操作")
                    .build()
            ))
            .requiredTools(Arrays.asList("git-log", "git-branch", "git-status"))
            .build();
    }
    
    // ==================== 文件操作技能 ====================
    
    /**
     * 批量文件处理技能
     */
    public static Skill batchFileSkill() {
        return Skill.builder()
            .id("batch-file-processing")
            .name("批量文件处理")
            .description("批量处理文件操作，如重命名、格式转换等")
            .category(Skill.Category.CODE)
            .tags(Arrays.asList("file", "batch", "automation"))
            .systemPrompt("""
                你是一个文件处理专家。请帮助用户批量处理文件：
                
                处理能力：
                1. 批量重命名 - 按规则重命名文件
                2. 格式转换 - 文件格式批量转换
                3. 内容替换 - 批量文本替换
                4. 文件组织 - 按规则移动、分类
                5. 生成脚本 - 创建自动化脚本
                
                处理原则：
                - 先预览后执行
                - 支持正则匹配
                - 保留原文件备份
                - 提供撤销方案
                
                输出：
                1. 处理计划
                2. 执行的命令/代码
                3. 预期结果
                4. 注意事项
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("将所有 .txt 文件转换为 .md")
                    .description("批量文件格式转换")
                    .build()
            ))
            .requiredTools(Arrays.asList("glob-search", "file-read", "file-write"))
            .build();
    }
    
    /**
     * 代码搜索技能
     */
    public static Skill codeSearchSkill() {
        return Skill.builder()
            .id("code-search")
            .name("代码搜索")
            .description("智能搜索代码，支持语义理解和模式匹配")
            .category(Skill.Category.ANALYSIS)
            .tags(Arrays.asList("search", "code", "pattern"))
            .systemPrompt("""
                你是一个代码搜索专家。请帮助用户查找代码：
                
                搜索能力：
                1. 语义搜索 - 理解意图找相关代码
                2. 模式匹配 - 正则、通配符搜索
                3. 跨文件搜索 - 追踪调用链
                4. 符号搜索 - 类、方法、变量
                5. 搜索建议 - 优化搜索词
                
                搜索结果：
                - 代码片段
                - 文件位置
                - 相关上下文
                - 调用关系
                
                输出格式：
                1. 搜索策略
                2. 匹配结果
                3. 结果分析
                4. 相关推荐
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("找到所有使用了 ThreadPoolExecutor 的地方")
                    .description("API 使用情况搜索")
                    .build()
            ))
            .requiredTools(Arrays.asList("grep-search", "glob-search", "symbol-search"))
            .build();
    }
    
    // ==================== API/HTTP 相关技能 ====================
    
    /**
     * API 客户端生成技能
     */
    public static Skill apiClientSkill() {
        return Skill.builder()
            .id("api-client-generation")
            .name("API 客户端生成")
            .description("根据 API 文档生成客户端代码")
            .category(Skill.Category.CODE)
            .tags(Arrays.asList("api", "http", "client", "rest"))
            .systemPrompt("""
                你是一个 API 客户端生成专家。请根据 API 规范生成客户端代码：
                
                生成内容：
                1. 数据模型 - 请求/响应 DTO
                2. 客户端类 - API 调用封装
                3. 错误处理 - 异常处理机制
                4. 认证逻辑 - 认证信息处理
                5. 使用示例 - 调用示例代码
                
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
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("为 OpenAPI 规范生成 Java 客户端")
                    .description("从 API 文档生成代码")
                    .build()
            ))
            .requiredTools(Arrays.asList("file-read", "file-write"))
            .build();
    }
    
    /**
     * HTTP 请求构建技能
     */
    public static Skill httpRequestSkill() {
        return Skill.builder()
            .id("http-request-builder")
            .name("HTTP 请求构建")
            .description("生成 HTTP 请求代码（curl、Python、Java等）")
            .category(Skill.Category.CODE)
            .tags(Arrays.asList("http", "api", "curl", "request"))
            .systemPrompt("""
                你是一个 HTTP 请求专家。请生成各种格式的 HTTP 请求代码：
                
                支持格式：
                1. cURL - 命令行工具
                2. Python - requests/http.client
                3. Java - HttpClient/OkHttp
                4. JavaScript - fetch/axios
                5. PowerShell - Invoke-RestMethod
                
                请求处理：
                - GET/POST/PUT/DELETE
                - Headers 设置
                - 认证信息
                - 请求体构造
                - 文件上传
                
                输出要求：
                - 可直接运行的代码
                - 参数说明
                - 响应处理示例
                - 错误处理
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("POST 请求到 /api/users，包含 JSON 体")
                    .description("REST API POST 请求")
                    .build()
            ))
            .requiredTools(Arrays.asList("file-write"))
            .build();
    }
    
    // ==================== 通用技能 ====================
    
    /**
     * 智能助手技能（默认）
     */
    public static Skill defaultAssistantSkill() {
        return Skill.builder()
            .id("default-assistant")
            .name("智能助手")
            .description("通用助手，回答各种编程和技术问题")
            .category(Skill.Category.CUSTOM)
            .tags(Arrays.asList("general", "assistant", "help"))
            .systemPrompt("""
                你是一个专业的编程助手。请帮助用户解决各种技术问题：
                
                能力范围：
                - 编程语言问题
                - 框架使用指导
                - 工具配置帮助
                - 最佳实践建议
                - 技术方案评估
                
                回答原则：
                1. 准确 - 提供正确的信息
                2. 实用 - 给出可执行的方案
                3. 简洁 - 避免冗余说明
                4. 友好 - 使用易懂的语言
                
                如果问题超出范围，礼貌地说明并提供替代建议。
                """)
            .examples(Arrays.asList(
                Skill.Example.builder()
                    .input("Spring Boot 如何配置数据库连接池？")
                    .description("框架配置问题")
                    .build()
            ))
            .requiredTools(Arrays.asList())
            .build();
    }
    
    // ==================== 获取所有技能 ====================
    
    /**
     * 获取所有内置技能
     */
    public static List<Skill> getAllSkills() {
        return Arrays.asList(
            // 代码相关
            codeGenerationSkill(),
            codeRefactoringSkill(),
            codeReviewSkill(),
            unitTestSkill(),
            
            // 文档相关
            docGenerationSkill(),
            codeExplanationSkill(),
            
            // 分析相关
            projectAnalysisSkill(),
            bugDiagnosisSkill(),
            performanceAnalysisSkill(),
            
            // Git 相关
            commitMessageSkill(),
            gitAssistantSkill(),
            
            // 文件操作
            batchFileSkill(),
            codeSearchSkill(),
            
            // API/HTTP
            apiClientSkill(),
            httpRequestSkill(),
            
            // 通用
            defaultAssistantSkill()
        );
    }
    
    /**
     * 根据 ID 获取技能
     */
    public static Skill getSkillById(String id) {
        return getAllSkills().stream()
            .filter(skill -> skill.getId().equals(id))
            .findFirst()
            .orElse(defaultAssistantSkill());
    }
    
    /**
     * 根据分类获取技能
     */
    public static List<Skill> getSkillsByCategory(Skill.Category category) {
        return getAllSkills().stream()
            .filter(skill -> skill.getCategory() == category)
            .toList();
    }
    
    /**
     * 搜索技能
     */
    public static List<Skill> searchSkills(String query) {
        String lowerQuery = query.toLowerCase();
        return getAllSkills().stream()
            .filter(skill -> 
                skill.getName().toLowerCase().contains(lowerQuery) ||
                skill.getId().toLowerCase().contains(lowerQuery) ||
                skill.getTags().stream().anyMatch(tag -> tag.toLowerCase().contains(lowerQuery))
            )
            .toList();
    }
}
