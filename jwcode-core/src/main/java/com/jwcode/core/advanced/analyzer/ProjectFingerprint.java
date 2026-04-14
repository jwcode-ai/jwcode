package com.jwcode.core.advanced.analyzer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 项目指纹 - 快速识别项目类型和架构特征
 * 
 * 设计原则：不递归扫描，仅通过根目录标志性文件判断项目类型，
 * 实现"毫秒级"项目识别，避免被海量文件淹没。
 */
public class ProjectFingerprint {
    
    public enum ProjectType {
        MAVEN_SPRING_BOOT("Maven + Spring Boot"),
        MAVEN("Maven"),
        GRADLE("Gradle"),
        NPM_REACT("npm + React/Vite"),
        NPM("npm/Node.js"),
        PYTHON("Python"),
        GO("Go"),
        RUST("Rust"),
        GENERIC("Generic");
        
        private final String displayName;
        
        ProjectType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private final Path root;
    private ProjectType projectType;
    private final List<String> indicators = new ArrayList<>();
    private final Map<String, Object> metadata = new LinkedHashMap<>();
    
    public ProjectFingerprint(Path root) {
        this.root = root;
        analyze();
    }
    
    private void analyze() {
        // 1. 检测 Spring Boot Maven
        if (exists("pom.xml")) {
            indicators.add("pom.xml");
            if (fileContains("pom.xml", "spring-boot")) {
                projectType = ProjectType.MAVEN_SPRING_BOOT;
                indicators.add("spring-boot dependency");
            } else {
                projectType = ProjectType.MAVEN;
            }
            
            // 检测模块数
            long moduleCount = countFilesMatching("*/pom.xml") - 1; // 减去根 pom
            if (moduleCount > 0) {
                metadata.put("mavenModules", moduleCount);
            }
        }
        // 2. 检测 Gradle
        else if (exists("build.gradle") || exists("build.gradle.kts") || exists("settings.gradle")) {
            projectType = ProjectType.GRADLE;
            indicators.addAll(List.of("build.gradle", "settings.gradle"));
        }
        // 3. 检测 npm / React
        else if (exists("package.json")) {
            indicators.add("package.json");
            if (fileContains("package.json", "react") || fileContains("package.json", "vite")) {
                projectType = ProjectType.NPM_REACT;
                indicators.add("React/Vite dependency");
            } else {
                projectType = ProjectType.NPM;
            }
        }
        // 4. 检测 Python
        else if (exists("requirements.txt") || exists("pyproject.toml") || exists("setup.py")) {
            projectType = ProjectType.PYTHON;
            indicators.add("requirements.txt / pyproject.toml");
        }
        // 5. 检测 Go
        else if (exists("go.mod")) {
            projectType = ProjectType.GO;
            indicators.add("go.mod");
        }
        // 6. 检测 Rust
        else if (exists("Cargo.toml")) {
            projectType = ProjectType.RUST;
            indicators.add("Cargo.toml");
        }
        else {
            projectType = ProjectType.GENERIC;
        }
        
        // 通用附加指标
        if (exists("docker-compose.yml") || exists("Dockerfile")) {
            indicators.add("Docker");
        }
        if (exists(".github/workflows")) {
            indicators.add("GitHub Actions");
        }
        if (exists("README.md")) {
            indicators.add("README.md");
        }
    }
    
    private boolean exists(String relativePath) {
        return Files.exists(root.resolve(relativePath));
    }
    
    private boolean fileContains(String relativePath, String keyword) {
        Path path = root.resolve(relativePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return false;
        }
        try {
            String content = Files.readString(path).toLowerCase();
            return content.contains(keyword.toLowerCase());
        } catch (Exception e) {
            return false;
        }
    }
    
    private long countFilesMatching(String pattern) {
        try (var stream = Files.find(root, 3, (p, attrs) -> {
            return p.getFileName() != null && p.getFileName().toString().equals("pom.xml");
        })) {
            return stream.count();
        } catch (Exception e) {
            return 0;
        }
    }
    
    public ProjectType getProjectType() {
        return projectType;
    }
    
    public List<String> getIndicators() {
        return Collections.unmodifiableList(indicators);
    }
    
    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }
    
    /**
     * 根据项目类型，返回高信息密度的关键文件路径模板
     */
    public List<String> getHighValueFilePatterns() {
        List<String> patterns = new ArrayList<>();
        
        switch (projectType) {
            case MAVEN_SPRING_BOOT, MAVEN -> {
                patterns.add("pom.xml");
                patterns.add("**/src/main/resources/application*.yml");
                patterns.add("**/src/main/resources/application*.yaml");
                patterns.add("**/src/main/resources/application*.properties");
                patterns.add("**/src/main/java/**/*App*.java");
                patterns.add("**/src/main/java/**/*Application*.java");
                patterns.add("**/src/main/java/**/config/*Config*.java");
            }
            case GRADLE -> {
                patterns.add("build.gradle*");
                patterns.add("settings.gradle");
                patterns.add("**/src/main/java/**/*Application*.java");
            }
            case NPM_REACT, NPM -> {
                patterns.add("package.json");
                patterns.add("vite.config.*");
                patterns.add("tsconfig.json");
                patterns.add("src/App.*");
                patterns.add("src/main.*");
            }
            case PYTHON -> {
                patterns.add("requirements.txt");
                patterns.add("pyproject.toml");
                patterns.add("main.py");
                patterns.add("app.py");
            }
            case GO -> {
                patterns.add("go.mod");
                patterns.add("main.go");
                patterns.add("cmd/**/*.go");
            }
            case RUST -> {
                patterns.add("Cargo.toml");
                patterns.add("src/main.rs");
                patterns.add("src/lib.rs");
            }
            default -> {
                patterns.add("README*");
                patterns.add("Makefile");
            }
        }
        
        // 通用高价值文件
        patterns.add("docker-compose.yml");
        patterns.add("Dockerfile");
        patterns.add("README.md");
        
        return patterns;
    }
}
