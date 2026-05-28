package com.jwcode.core.service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.*;
import java.util.stream.*;

/**
 * 项目自文档生成器 — 对标 Claude Code /init。
 *
 * <p>分析项目结构、模块、配置、Agent、工具、Hook 等，
 * 自动生成/更新 README.md、AGENTS.md、docs/ 下的版本记录。</p>
 */
public class ProjectDocGenerator {
    private static final Logger logger = Logger.getLogger(ProjectDocGenerator.class.getName());

    private final Path projectRoot;

    public ProjectDocGenerator(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    /** 生成/刷新所有文档 */
    public DocResult generateAll() {
        DocResult result = new DocResult();
        try {
            ProjectInfo info = analyze();
            result.updated(updateReadme(info));
            result.updated(updateAgentsMd(info));
            result.updated(updateArchitectureDoc(info));
            result.setSummary(info.summary());
            logger.info("[DocGen] " + result);
        } catch (Exception e) {
            logger.severe("[DocGen] Failed: " + e.getMessage());
            result.errors.add(e.getMessage());
        }
        return result;
    }

    /** 分析项目 */
    ProjectInfo analyze() throws IOException {
        ProjectInfo info = new ProjectInfo();
        info.root = projectRoot;
        info.date = LocalDate.now();

        // 1. 模块列表 (从 jwcode-parent/pom.xml)
        Path parentPom = projectRoot.resolve("jwcode-parent/pom.xml");
        if (Files.exists(parentPom)) {
            String pom = Files.readString(parentPom);
            Matcher m = Pattern.compile("<module>\\.\\./([^<]+)</module>").matcher(pom);
            while (m.find()) info.modules.add(m.group(1));
            // 版本
            Matcher vm = Pattern.compile("<version>([^<]+)</version>").matcher(pom);
            if (vm.find()) info.version = vm.group(1);
        }

        // 2. Agent 列表
        Path agentDir = projectRoot.resolve("jwcode-core/src/main/java/com/jwcode/core/agent");
        if (Files.exists(agentDir)) {
            try (var s = Files.list(agentDir)) {
                info.agents = s.filter(f -> f.getFileName().toString().endsWith("Agent.java"))
                    .map(f -> f.getFileName().toString().replace(".java", ""))
                    .sorted().toList();
            }
        }

        // 3. 工具列表
        Path toolDir = projectRoot.resolve("jwcode-core/src/main/java/com/jwcode/core/tool");
        if (Files.exists(toolDir)) {
            try (var s = Files.list(toolDir)) {
                info.tools = s.filter(f -> f.getFileName().toString().endsWith("Tool.java"))
                    .map(f -> f.getFileName().toString().replace(".java", ""))
                    .sorted().toList();
            }
        }

        // 4. Hook 组件
        Path hookDir = projectRoot.resolve("jwcode-core/src/main/java/com/jwcode/core/hook");
        if (Files.exists(hookDir)) info.hasHooks = true;

        // 5. Python CLI
        info.hasPythonCli = Files.exists(projectRoot.resolve("python-cli/pyproject.toml"));

        // 6. Web UI
        info.hasWebUi = Files.exists(projectRoot.resolve("jwcode-web/src/App.tsx"));

        // 7. 配置文件
        info.configFiles = findConfigs();

        // 8. 统计
        info.javaFiles = countJavaFiles(projectRoot.resolve("jwcode-core"));
        info.frontendFiles = info.hasWebUi ? countFiles(projectRoot.resolve("jwcode-web/src"), ".ts", ".tsx") : 0;
        info.pythonFiles = info.hasPythonCli ? countFiles(projectRoot.resolve("python-cli"), ".py") : 0;

        return info;
    }

    private List<String> findConfigs() {
        List<String> configs = new ArrayList<>();
        for (String f : new String[]{"pom.xml", "jwcode-parent/pom.xml", "start.bat", "start.sh",
            "python-cli/pyproject.toml", "jwcode-web/vite.config.ts", "jwcode-web/tailwind.config.js"}) {
            if (Files.exists(projectRoot.resolve(f))) configs.add(f);
        }
        return configs;
    }

    private int countJavaFiles(Path dir) {
        if (!Files.exists(dir)) return 0;
        try (var s = Files.walk(dir)) {
            return (int) s.filter(f -> f.toString().endsWith(".java")).count();
        } catch (IOException e) { return 0; }
    }

    private int countFiles(Path dir, String... exts) {
        if (!Files.exists(dir)) return 0;
        try (var s = Files.walk(dir)) {
            return (int) s.filter(f -> {
                String n = f.toString();
                for (String e : exts) if (n.endsWith(e)) return true;
                return false;
            }).count();
        } catch (IOException e) { return 0; }
    }

    // ───── 文档生成 ─────

    boolean updateReadme(ProjectInfo info) throws IOException {
        Path readme = projectRoot.resolve("README.md");
        String content = buildReadme(info);
        if (!Files.exists(readme) || !Files.readString(readme).equals(content)) {
            Files.writeString(readme, content);
            return true;
        }
        return false;
    }

    boolean updateAgentsMd(ProjectInfo info) throws IOException {
        Path agentsMd = projectRoot.resolve("AGENTS.md");
        if (!Files.exists(agentsMd)) return false;
        String existing = Files.readString(agentsMd);

        // 追加/更新版本信息到文件头部
        String versionLine = "> 最后自动更新：" + info.date + " | 版本 " + info.version
            + " | " + info.modules.size() + " 模块 | " + info.agents.size() + " Agent | " + info.tools.size() + " Tool";
        if (existing.contains("> 最后自动更新：")) {
            existing = existing.replaceAll("> 最后自动更新：.*", versionLine);
        } else {
            int i = existing.indexOf("\n---\n");
            if (i > 0) existing = existing.substring(0, i) + "\n" + versionLine + existing.substring(i);
        }
        Files.writeString(agentsMd, existing);
        return true;
    }

    boolean updateArchitectureDoc(ProjectInfo info) throws IOException {
        Path archDoc = projectRoot.resolve("docs/ARCHITECTURE_V2.md");
        if (!Files.exists(archDoc)) return false;
        String existing = Files.readString(archDoc);
        String header = "> 最后更新：" + info.date + " | 模块: " + String.join(", ", info.modules);
        existing = existing.replaceAll("> 最后更新：.*", header);
        Files.writeString(archDoc, existing);
        return true;
    }

    String buildReadme(ProjectInfo info) {
        String t = info.tools.size() > 0 ? info.tools.get(0).replace("Tool", "") : "Unknown";
        String agentList = info.agents.stream().limit(6)
            .map(a -> "- `" + a + "`").collect(Collectors.joining("\n"));
        String moduleList = info.modules.stream()
            .map(m -> "| `" + m + "` | |").collect(Collectors.joining("\n"));

        return """
            # JWCode

            > Java AI Coding Tool — v%s | %d 模块 | %d Agent | %d Tool
            > 最后自动生成: %s

            ## 快速开始

            ```bash
            ./start.bat          # Windows
            ./start.sh           # macOS / Linux
            jwcode start         # Python CLI 一键启动
            ```

            ## 模块

            | 模块 | 说明 |
            |------|------|
            %s

            ## Agent 清单

            %s

            ## 统计

            - Java 源文件: %d
            - 前端文件: %d
            - Python 文件: %d

            ## 配置

            编辑 `~/.jwcode/config.yaml`
            """.formatted(
                info.version, info.modules.size(), info.agents.size(), info.tools.size(),
                info.date, moduleList, agentList,
                info.javaFiles, info.frontendFiles, info.pythonFiles);
    }

    // ───── 数据类 ─────

    static class ProjectInfo {
        Path root;
        LocalDate date;
        String version = "1.0.0-SNAPSHOT";
        List<String> modules = new ArrayList<>();
        List<String> agents = new ArrayList<>();
        List<String> tools = new ArrayList<>();
        boolean hasHooks, hasPythonCli, hasWebUi;
        List<String> configFiles = new ArrayList<>();
        int javaFiles, frontendFiles, pythonFiles;

        String summary() {
            return String.format("%s | v%s | %d modules | %d agents | %d tools | %d java files",
                date, version, modules.size(), agents.size(), tools.size(), javaFiles);
        }
    }

    public static void main(String[] args) {
        Path root = args.length > 0 ? Path.of(args[0]) : Path.of(".");
        ProjectDocGenerator gen = new ProjectDocGenerator(root);
        DocResult result = gen.generateAll();
        System.out.println(result);
        System.out.println("Summary: " + result.summary);
    }

    public static class DocResult {
        public final List<String> updated = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public String summary = "";
        void updated(boolean changed) { if (changed) updated.add("✓"); }
        void setSummary(String s) { summary = s; }
        public String toString() { return "DocResult(updated=" + updated.size() + ", errors=" + errors.size() + ")"; }
    }
}