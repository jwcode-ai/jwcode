package com.jwcode.core.advanced.analyzer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 假设推导引擎 - 从配置文件中提取关键线索，推导需要验证的代码文件
 * 
 * 核心能力：
 * 1. 读取 application.yaml / application.properties / package.json 等配置
 * 2. 提取类名、包名、开关项、依赖项
 * 3. 根据这些线索，推导并推荐需要进一步阅读的代码文件
 * 
 * 示例：
 * - 看到 spring.datasource.url → 推导需要查看 DataSourceConfig.java
 * - 看到 jwclaw.distributed.enabled=true → 推导需要查看 AgentClusterManager.java
 * - 看到 dependencies 包含 react-router → 推导需要查看路由配置文件
 */
public class HypothesisEngine {
    
    private final Path root;
    private final ProjectFingerprint fingerprint;
    
    public HypothesisEngine(Path root, ProjectFingerprint fingerprint) {
        this.root = root;
        this.fingerprint = fingerprint;
    }
    
    /**
     * 从已收集的证据中生成假设，并推荐需要验证的文件
     */
    public List<Hypothesis> generateHypotheses(List<EvidenceCollector.EvidenceFile> evidenceFiles) {
        List<Hypothesis> hypotheses = new ArrayList<>();
        Set<String> seenDescriptions = new LinkedHashSet<>();
        
        for (EvidenceCollector.EvidenceFile evidence : evidenceFiles) {
            if (isConfigFile(evidence.path())) {
                String content = readContentSafely(evidence.path());
                if (content != null && !content.isEmpty()) {
                    for (Hypothesis h : analyzeConfigContent(evidence.path(), content)) {
                        // 按描述去重，保留置信度最高的（因为前面可能已经有相同描述的）
                        if (!seenDescriptions.contains(h.description())) {
                            seenDescriptions.add(h.description());
                            hypotheses.add(h);
                        }
                    }
                }
            }
        }
        
        // 按置信度排序
        hypotheses.sort(Comparator.comparingInt(Hypothesis::confidence).reversed());
        return hypotheses;
    }
    
    private List<Hypothesis> analyzeConfigContent(Path configPath, String content) {
        List<Hypothesis> result = new ArrayList<>();
        String lowerContent = content.toLowerCase();
        
        // 假设 1：分布式/集群开关
        if (containsAny(lowerContent, "distributed.enabled", "cluster.enabled", "swarm.enabled")) {
            result.add(new Hypothesis(
                "分布式功能已开启，需验证集群管理实现",
                List.of("**/*Cluster*Manager*.java", "**/*Cluster*.java", "**/*Distributed*.java"),
                95
            ));
        }
        
        // 假设 2：数据库/数据源配置
        if (containsAny(lowerContent, "datasource", "spring.datasource", "database.url", "jdbc")) {
            result.add(new Hypothesis(
                "检测到数据库配置，需验证数据访问层实现",
                List.of("**/*DataSource*.java", "**/*Repository*.java", "**/*Dao*.java", "**/*Mapper*.java"),
                90
            ));
        }
        
        // 假设 3：Temporal / 工作流
        if (containsAny(lowerContent, "temporal", "workflow", "camunda", "activiti")) {
            result.add(new Hypothesis(
                "检测到工作流引擎配置，需验证工作流实现",
                List.of("**/*Workflow*.java", "**/*Temporal*.java", "**/*Activity*.java"),
                90
            ));
        }
        
        // 假设 4：模型池 / AI 客户端
        if (containsAny(lowerContent, "model.pool", "openai", "deepseek", "moonshot", "modelclient", "llm")) {
            result.add(new Hypothesis(
                "检测到 AI 模型配置，需验证模型调用实现",
                List.of("**/*Model*Client*.java", "**/*ModelPool*.java", "**/*LLM*.java"),
                90
            ));
        }
        
        // 假设 5：租户 / 多租户
        if (containsAny(lowerContent, "tenant", "multi-tenant", "tenantid", "tenant_id")) {
            result.add(new Hypothesis(
                "检测到多租户配置，需验证租户隔离实现",
                List.of("**/*Tenant*.java", "**/*TenantContext*.java", "**/*TenantInterceptor*.java"),
                88
            ));
        }
        
        // 假设 6：记忆 / 向量 / Embedding
        if (containsAny(lowerContent, "memory", "embedding", "vector", "pgvector", "milvus")) {
            result.add(new Hypothesis(
                "检测到记忆/向量配置，需验证记忆系统实现",
                List.of("**/*Memory*.java", "**/*Embedding*.java", "**/*Vector*.java"),
                88
            ));
        }
        
        // 假设 7：WebSocket / 实时通信
        if (containsAny(lowerContent, "websocket", "socket.io", "sse", "server-sent")) {
            result.add(new Hypothesis(
                "检测到实时通信配置，需验证 WebSocket 处理器",
                List.of("**/*WebSocket*.java", "**/*Socket*.java", "**/*Handler*.java"),
                85
            ));
        }
        
        // 假设 8：缓存 / Redis
        if (containsAny(lowerContent, "redis", "cache", "caching", "lettuce")) {
            result.add(new Hypothesis(
                "检测到缓存配置，需验证缓存管理实现",
                List.of("**/*Cache*.java", "**/*Redis*.java", "**/*CacheConfig*.java"),
                85
            ));
        }
        
        // 假设 9：安全 / JWT / OAuth
        if (containsAny(lowerContent, "jwt", "security", "oauth", "spring.security", "auth")) {
            result.add(new Hypothesis(
                "检测到安全配置，需验证认证授权实现",
                List.of("**/*Security*.java", "**/*Jwt*.java", "**/*Auth*.java", "**/*OAuth*.java"),
                85
            ));
        }
        
        // 假设 10：定时任务 / Quartz
        if (containsAny(lowerContent, "cron", "quartz", "scheduled", "scheduler")) {
            result.add(new Hypothesis(
                "检测到定时任务配置，需验证调度实现",
                List.of("**/*Scheduler*.java", "**/*Cron*.java", "**/*Job*.java"),
                80
            ));
        }
        
        // 假设 11：Docker / K8s
        if (containsAny(lowerContent, "docker", "kubernetes", "k8s", "container")) {
            result.add(new Hypothesis(
                "检测到容器化配置，需验证部署相关文件",
                List.of("Dockerfile", "docker-compose.yml", "**/k8s/**/*.yaml"),
                75
            ));
        }
        
        // Maven/Gradle 特定：从依赖中推导框架
        if (configPath.getFileName().toString().equals("pom.xml")) {
            if (lowerContent.contains("spring-boot-starter-webflux")) {
                result.add(new Hypothesis(
                    "使用 Spring WebFlux，需验证 Reactive 控制器",
                    List.of("**/*Router*.java", "**/*Handler*.java"),
                    85
                ));
            }
            if (lowerContent.contains("mybatis")) {
                result.add(new Hypothesis(
                    "使用 MyBatis，需验证 Mapper 接口和 XML",
                    List.of("**/*Mapper.java", "**/mapper/*.xml"),
                    85
                ));
            }
        }
        
        return result;
    }
    
    /**
     * 验证假设：在项目中搜索假设推荐的文件，返回实际存在的文件路径
     */
    public List<Path> verifyHypothesis(Hypothesis hypothesis) {
        List<Path> verified = new ArrayList<>();
        for (String pattern : hypothesis.suggestedPatterns()) {
            verified.addAll(findFilesByPattern(pattern));
        }
        return verified.stream().distinct().toList();
    }
    
    private List<Path> findFilesByPattern(String pattern) {
        List<Path> found = new ArrayList<>();
        try {
            var matcher = root.getFileSystem().getPathMatcher("glob:" + pattern);
            Files.walk(root, 8).forEach(p -> {
                if (Files.isRegularFile(p) && !NoiseFilter.isNoise(p)) {
                    String rel = root.relativize(p).toString().replace('\\', '/');
                    Path relPath = root.relativize(p);
                    // 尝试用 PathMatcher 匹配相对路径
                    if (matcher.matches(relPath) || matcher.matches(p)) {
                        found.add(p);
                    } else {
                        // 降级：简单后缀匹配
                        String lower = rel.toLowerCase();
                        String patLower = pattern.toLowerCase();
                        if (patLower.startsWith("**/")) {
                            String suffix = patLower.substring(3);
                            if (suffix.contains("*")) {
                                String prefix = suffix.substring(0, suffix.indexOf('*'));
                                String end = suffix.substring(suffix.indexOf('*') + 1);
                                if (lower.contains(prefix) && lower.endsWith(end)) {
                                    found.add(p);
                                }
                            } else if (lower.endsWith(suffix)) {
                                found.add(p);
                            }
                        }
                    }
                }
            });
        } catch (Exception e) {
            // ignore
        }
        return found;
    }
    
    private boolean isConfigFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".yml") || name.endsWith(".yaml") || 
               name.endsWith(".properties") || name.equals("pom.xml") ||
               name.equals("package.json") || name.equals("build.gradle") ||
               name.equals("docker-compose.yml");
    }
    
    private String readContentSafely(Path path) {
        try {
            long size = Files.size(path);
            if (size > 512 * 1024) {
                return "[TOO_LARGE]";
            }
            return Files.readString(path);
        } catch (Exception e) {
            return null;
        }
    }
    
    private boolean containsAny(String text, String... keywords) {
        // 将文本中的空白（空格、换行、冒号后的缩进）规范化，便于跨行匹配
        String normalized = text.toLowerCase()
            .replaceAll("\\s+", " ")
            .replaceAll(": ", ".")
            .replaceAll(" ", ".");
        for (String kw : keywords) {
            String normalizedKw = kw.toLowerCase().replace(" ", ".");
            if (normalized.contains(normalizedKw)) {
                return true;
            }
            // 同时保留原始行级匹配（用于简单情况）
            if (text.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    public record Hypothesis(
        String description,
        List<String> suggestedPatterns,
        int confidence
    ) {}
}
