package com.jwcode.core.service;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * /doctor — 对标 Claude Code /doctor 的自诊断服务。
 * 检查：Java 版本 → Maven → 配置 → API Key → 网络 → 权限 → Docker → 磁盘空间。
 */
public class DoctorService {
    private static final Logger logger = Logger.getLogger(DoctorService.class.getName());

    public record CheckResult(String name, boolean ok, String detail) {
        public String icon() { return ok ? "✅" : "❌"; }
        public String toString() { return icon() + " " + name + ": " + detail; }
    }

    public List<CheckResult> runAll() {
        List<CheckResult> results = new ArrayList<>();
        results.add(checkJava());
        results.add(checkMaven());
        results.add(checkConfig());
        results.add(checkApiKey());
        results.add(checkNetwork());
        results.add(checkWorkspace());
        results.add(checkDocker());
        results.add(checkDiskSpace());
        return results;
    }

    private CheckResult checkJava() {
        String v = System.getProperty("java.version");
        boolean ok = v != null && (v.startsWith("17") || v.startsWith("21") || v.startsWith("22"));
        return new CheckResult("Java", ok, ok ? "Java " + v : "Need Java 17+");
    }

    private CheckResult checkMaven() {
        try {
            Process p = new ProcessBuilder("mvn", "--version").start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                return new CheckResult("Maven", true, line != null ? line : "installed");
            }
        } catch (IOException e) { return new CheckResult("Maven", false, "not found"); }
    }

    private CheckResult checkConfig() {
        Path config = Path.of(System.getProperty("user.home"), ".jwcode", "config.yaml");
        return new CheckResult("Config", Files.exists(config),
            Files.exists(config) ? "~/.jwcode/config.yaml" : "missing ~/.jwcode/config.yaml");
    }

    private CheckResult checkApiKey() {
        Path config = Path.of(System.getProperty("user.home"), ".jwcode", "config.yaml");
        if (!Files.exists(config)) return new CheckResult("API Key", false, "config missing");
        try {
            String content = Files.readString(config);
            boolean hasKey = content.contains("api-keys") && !content.contains("your-api-key-here");
            return new CheckResult("API Key", hasKey, hasKey ? "configured" : "placeholder detected");
        } catch (IOException e) { return new CheckResult("API Key", false, e.getMessage()); }
    }

    private CheckResult checkNetwork() {
        try {
            new Socket("api.moonshot.cn", 443).close();
            return new CheckResult("Network", true, "api.moonshot.cn reachable");
        } catch (IOException e1) {
            try {
                new Socket("api.openai.com", 443).close();
                return new CheckResult("Network", true, "api.openai.com reachable");
            } catch (IOException e2) { return new CheckResult("Network", false, "no API reachable"); }
        }
    }

    private CheckResult checkWorkspace() {
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        return new CheckResult("Workspace", Files.isDirectory(cwd) && Files.isWritable(cwd),
            cwd.toString());
    }

    private CheckResult checkDocker() {
        try {
            Process p = new ProcessBuilder("docker", "info").start();
            int rc = p.waitFor();
            return new CheckResult("Docker", rc == 0, rc == 0 ? "available" : "not running");
        } catch (Exception e) { return new CheckResult("Docker", false, "not found"); }
    }

    private CheckResult checkDiskSpace() {
        Path cwd = Path.of(System.getProperty("user.dir", "."));
        try {
            long free = Files.getFileStore(cwd).getUsableSpace() / (1024 * 1024 * 1024);
            return new CheckResult("Disk Space", free > 1, free + " GB free");
        } catch (IOException e) { return new CheckResult("Disk Space", false, e.getMessage()); }
    }
}
