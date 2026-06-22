package com.jwcode.core.service;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

/**
 * /doctor — 对标 Codex doctor 的自诊断服务。
 *
 * 检查分类（与 Codex 对齐）：
 *   system     — Java版本、OS、CPU核数、可用内存
 *   config     — 配置文件存在性、YAML schema 校验、API key 配置
 *   network    — API endpoint 连通性（HTTP ping）
 *   websocket  — WebSocket 握手延迟
 *   workspace  — 当前目录可读写、磁盘剩余空间
 *   runtime    — Docker/Maven 可选依赖
 *   updates    — 最新版本检查（GitHub releases）
 */
public class DoctorService {
    private static final Logger logger = Logger.getLogger(DoctorService.class.getName());

    public enum CheckStatus { OK, WARNING, FAIL }

    public static class DoctorIssue {
        public String severity;   // "error" | "warning"
        public String cause;
        public String measured;
        public String expected;
        public String remedy;

        public DoctorIssue(String severity, String cause, String measured, String expected, String remedy) {
            this.severity = severity;
            this.cause = cause;
            this.measured = measured;
            this.expected = expected;
            this.remedy = remedy;
        }
    }

    public static class DoctorCheck {
        public String id;
        public String category;
        public CheckStatus status;
        public String summary;
        public String detail;
        public List<DoctorIssue> issues;
        public long durationMs;

        public DoctorCheck(String id, String category, CheckStatus status, String summary, String detail) {
            this.id = id;
            this.category = category;
            this.status = status;
            this.summary = summary;
            this.detail = detail;
            this.issues = new ArrayList<>();
        }

        public void addIssue(DoctorIssue issue) {
            this.issues.add(issue);
        }
    }

    public static class DoctorReport {
        public int schemaVersion = 1;
        public String timestamp;
        public CheckStatus overallStatus;
        public String platform;
        public String cliVersion;
        public List<DoctorCheck> checks;
    }

    // ---- Legacy flat results (backward compat) ----

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

    // ---- Structured report (new API) ----

    public DoctorReport runFullReport(String backendUrl, int wsPort) {
        DoctorReport report = new DoctorReport();
        report.timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date());
        report.platform = System.getProperty("os.name") + " " + System.getProperty("os.arch");
        report.cliVersion = getVersion();
        report.checks = new ArrayList<>();

        report.checks.add(checkJavaStructured());
        report.checks.add(checkOSStructured());
        report.checks.add(checkConfigStructured());
        report.checks.add(checkApiKeyStructured());
        report.checks.add(checkNetworkStructured(backendUrl));
        if (wsPort > 0) {
            report.checks.add(checkWebSocketStructured(wsPort));
        }
        report.checks.add(checkWorkspaceStructured());
        report.checks.add(checkDiskSpaceStructured());
        report.checks.add(checkDockerStructured());
        report.checks.add(checkUpdatesStructured());

        report.overallStatus = ComputeOverallStatus(report.checks);
        return report;
    }

    private static CheckStatus ComputeOverallStatus(List<DoctorCheck> checks) {
        boolean anyFail = false;
        boolean anyWarn = false;
        for (DoctorCheck c : checks) {
            if (c.status == CheckStatus.FAIL) anyFail = true;
            if (c.status == CheckStatus.WARNING) anyWarn = true;
        }
        if (anyFail) return CheckStatus.FAIL;
        if (anyWarn) return CheckStatus.WARNING;
        return CheckStatus.OK;
    }

    // ---- New structured checks ----

    private DoctorCheck checkJavaStructured() {
        long start = System.currentTimeMillis();
        String v = System.getProperty("java.version");
        String vm = System.getProperty("java.vm.name", "");
        String home = System.getProperty("java.home", "");
        boolean ok = v != null && !v.startsWith("1.");

        DoctorCheck check = new DoctorCheck("java_version", "system",
            ok ? CheckStatus.OK : CheckStatus.FAIL,
            ok ? "Java " + v : "Java version too old",
            "version=" + v + "; vm=" + vm + "; home=" + home);

        if (!ok) {
            check.addIssue(new DoctorIssue("error",
                "Java version is below required minimum",
                v != null ? "Java " + v : "unknown",
                "Java 17+",
                "Install JDK 17+ from https://adoptium.net/download/"));
        }
        check.durationMs = System.currentTimeMillis() - start;
        return check;
    }

    private DoctorCheck checkOSStructured() {
        long start = System.currentTimeMillis();
        String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        int cores = Runtime.getRuntime().availableProcessors();
        long maxMem = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        String detail = "os=" + os + "; arch=" + arch + "; cores=" + cores + "; maxHeapMB=" + maxMem;

        DoctorCheck check = new DoctorCheck("os_info", "system",
            CheckStatus.OK, os + " " + arch + " (" + cores + " cores)", detail);
        check.durationMs = System.currentTimeMillis() - start;
        return check;
    }

    private DoctorCheck checkConfigStructured() {
        long start = System.currentTimeMillis();
        Path config = Path.of(System.getProperty("user.home"), ".jwcode", "config.yaml");
        boolean exists = Files.exists(config);

        DoctorCheck check = new DoctorCheck("config_file", "config",
            exists ? CheckStatus.OK : CheckStatus.FAIL,
            exists ? "~/.jwcode/config.yaml exists" : "Config file missing",
            config.toString());

        if (!exists) {
            check.addIssue(new DoctorIssue("error",
                "Configuration file not found",
                "missing", "~/.jwcode/config.yaml",
                "Run 'jwcode setup' or create ~/.jwcode/config.yaml"));
        } else {
            // basic YAML structure validation
            try {
                String content = Files.readString(config);
                if (!content.contains("providers:") && !content.contains("backend_url")) {
                    check.status = CheckStatus.WARNING;
                    check.addIssue(new DoctorIssue("warning",
                        "Config may be incomplete",
                        "No providers section detected",
                        "Config should define at least one LLM provider",
                        "See docs/CONFIG_GUIDE.md for the config schema"));
                }
            } catch (IOException e) {
                logger.warning("Cannot read config for validation: " + e.getMessage());
            }
        }
        check.durationMs = System.currentTimeMillis() - start;
        return check;
    }

    private DoctorCheck checkApiKeyStructured() {
        long start = System.currentTimeMillis();
        Path config = Path.of(System.getProperty("user.home"), ".jwcode", "config.yaml");

        DoctorCheck check = new DoctorCheck("api_key", "config",
            CheckStatus.FAIL, "API key not configured", "");

        if (Files.exists(config)) {
            try {
                String content = Files.readString(config);
                boolean hasKeys = content.contains("api-keys:");
                boolean hasPlaceholder = content.contains("your-api-key-here") || content.contains("sk-your");
                if (hasKeys && !hasPlaceholder) {
                    // check if any key looks real (non-placeholder, reasonable length)
                    boolean hasRealKey = content.lines()
                        .filter(l -> l.contains("sk-") || l.contains("ant-") || l.contains("hk"))
                        .anyMatch(l -> l.trim().length() > 30);
                    if (hasRealKey) {
                        check.status = CheckStatus.OK;
                        check.summary = "API key(s) configured";
                        check.detail = "At least one real API key found in config";
                    } else {
                        check.status = CheckStatus.WARNING;
                        check.summary = "API key may be a placeholder";
                        check.addIssue(new DoctorIssue("warning",
                            "API key looks like a placeholder",
                            "Short or placeholder key found",
                            "A valid API key (e.g. sk-...)",
                            "Edit ~/.jwcode/config.yaml and replace placeholder keys"));
                    }
                } else {
                    check.addIssue(new DoctorIssue("error",
                        "No api-keys section in config",
                        "missing api-keys", "api-keys: [sk-...]",
                        "Add your API key to providers.<name>.api-keys in config.yaml"));
                }
            } catch (IOException e) {
                logger.warning("Cannot read config for API key check: " + e.getMessage());
            }
        }
        check.durationMs = System.currentTimeMillis() - start;
        return check;
    }

    private DoctorCheck checkNetworkStructured(String backendUrl) {
        long start = System.currentTimeMillis();
        // Test API connectivity by hitting the backend's system status endpoint
        DoctorCheck check = new DoctorCheck("api_connectivity", "network",
            CheckStatus.FAIL, "API not reachable", "");

        if (backendUrl != null && !backendUrl.isEmpty()) {
            try {
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(backendUrl + "/api/system/status"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    check.status = CheckStatus.OK;
                    check.summary = "Backend API reachable";
                    check.detail = backendUrl + " responded 200 in " + check.durationMs + "ms";
                } else {
                    check.status = CheckStatus.WARNING;
                    check.summary = "Backend returned " + resp.statusCode();
                    check.addIssue(new DoctorIssue("warning",
                        "Backend returned non-200 status",
                        String.valueOf(resp.statusCode()), "200",
                        "Check backend logs for errors"));
                }
            } catch (IOException e) {
                check.addIssue(new DoctorIssue("error",
                    "Cannot connect to backend API",
                    e.getMessage(), "HTTP 200",
                    "Is the backend running? Start with: jwcode start"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                check.addIssue(new DoctorIssue("error", "Request interrupted", "", "", "Retry"));
            }
        } else {
            // Fall back to external API check
            String[] endpoints = {"api.openai.com", "api.anthropic.com", "api.deepseek.com"};
            for (String host : endpoints) {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(host, 443), 3000);
                    check.status = CheckStatus.OK;
                    check.summary = host + " reachable";
                    check.detail = "Connected to " + host + ":443";
                    break;
                } catch (IOException e) {
                    logger.fine("Cannot connect to " + host + ": " + e.getMessage());
                }
            }
            if (check.status == CheckStatus.FAIL) {
                check.addIssue(new DoctorIssue("error",
                    "No API endpoints reachable",
                    "Connection timeout to all endpoints",
                    "At least one API endpoint reachable",
                    "Check your network connection and firewall"));
            }
        }
        check.durationMs = System.currentTimeMillis() - start;
        return check;
    }

    private DoctorCheck checkWebSocketStructured(int wsPort) {
        long start = System.currentTimeMillis();
        DoctorCheck check = new DoctorCheck("websocket", "websocket",
            CheckStatus.FAIL, "WebSocket not checked", "");

        try {
            URI wsUri = new URI("ws://localhost:" + wsPort + "/ws");
            // Simple TCP connectivity check to the WS port
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("localhost", wsPort), 3000);
                check.status = CheckStatus.OK;
                check.summary = "WebSocket endpoint reachable";
                check.detail = "ws://localhost:" + wsPort + "/ws — TCP handshake OK";
            } catch (IOException e) {
                check.status = CheckStatus.FAIL;
                check.summary = "WebSocket port not open";
                check.detail = "Cannot connect to port " + wsPort;
                check.addIssue(new DoctorIssue("error",
                    "WebSocket port is not accepting connections",
                    e.getMessage(),
                    "Port " + wsPort + " should be listening",
                    "Check if backend started correctly. Port may be in use."));
            }
        } catch (URISyntaxException e) {
            check.addIssue(new DoctorIssue("error", "Invalid WS URI", e.getMessage(), "", ""));
        }
        check.durationMs = System.currentTimeMillis() - start;
        return check;
    }

    private DoctorCheck checkWorkspaceStructured() {
        long start = System.currentTimeMillis();
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        boolean isDir = Files.isDirectory(cwd);
        boolean writable = isDir && Files.isWritable(cwd);

        DoctorCheck check = new DoctorCheck("workspace", "workspace",
            writable ? CheckStatus.OK : CheckStatus.FAIL,
            writable ? "Workspace OK" : "Workspace not writable",
            cwd.toString());

        if (!writable) {
            check.addIssue(new DoctorIssue("error",
                "Current directory is not writable",
                cwd.toString(),
                "Directory must be writable for file operations",
                "Change to a writable directory and retry"));
        }
        check.durationMs = System.currentTimeMillis() - start;
        return check;
    }

    private DoctorCheck checkDiskSpaceStructured() {
        long start = System.currentTimeMillis();
        Path cwd = Path.of(System.getProperty("user.dir", "."));
        try {
            FileStore fs = Files.getFileStore(cwd);
            long totalGB = fs.getTotalSpace() / (1024 * 1024 * 1024);
            long freeGB = fs.getUsableSpace() / (1024 * 1024 * 1024);
            long usedGB = totalGB - freeGB;

            CheckStatus st = freeGB > 1 ? CheckStatus.OK : (freeGB > 0.5 ? CheckStatus.WARNING : CheckStatus.FAIL);
            DoctorCheck check = new DoctorCheck("disk_space", "system",
                st,
                freeGB + " GB free / " + totalGB + " GB total",
                "freeGB=" + freeGB + "; totalGB=" + totalGB + "; usedGB=" + usedGB +
                "; fs=" + fs.name() + "; dir=" + cwd);

            if (st == CheckStatus.WARNING || st == CheckStatus.FAIL) {
                check.addIssue(new DoctorIssue(st == CheckStatus.FAIL ? "error" : "warning",
                    "Low disk space on workspace filesystem",
                    freeGB + " GB free",
                    "> 1 GB recommended",
                    "Free up disk space on " + cwd));
            }
            check.durationMs = System.currentTimeMillis() - start;
            return check;
        } catch (IOException e) {
            DoctorCheck check = new DoctorCheck("disk_space", "system",
                CheckStatus.WARNING, "Cannot check disk space", e.getMessage());
            check.durationMs = System.currentTimeMillis() - start;
            return check;
        }
    }

    private DoctorCheck checkDockerStructured() {
        long start = System.currentTimeMillis();
        DoctorCheck check = new DoctorCheck("docker", "runtime",
            CheckStatus.OK, "Docker not needed (optional)", "");

        try {
            Process p = new ProcessBuilder("docker", "info").start();
            int rc = p.waitFor();
            if (rc == 0) {
                check.summary = "Docker available (sandbox support)";
                check.detail = "Docker daemon responding — sandboxed execution available";
            } else {
                check.status = CheckStatus.WARNING;
                check.summary = "Docker daemon not running";
                check.detail = "docker info exited with code " + rc;
            }
        } catch (Exception e) {
            check.summary = "Docker not installed (optional)";
            check.detail = "Install Docker for sandboxed command execution";
        }
        check.durationMs = System.currentTimeMillis() - start;
        return check;
    }

    private DoctorCheck checkUpdatesStructured() {
        long start = System.currentTimeMillis();
        String currentVersion = getVersion();
        DoctorCheck check = new DoctorCheck("updates", "updates",
            CheckStatus.OK, "Version " + currentVersion, "current=" + currentVersion);

        // Try to check latest version from GitHub
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/ngwlh/jwcode/releases/latest"))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                // Simple JSON extraction (avoid Jackson dependency here)
                String body = resp.body();
                String tag = extractJsonString(body, "tag_name");
                if (tag != null) {
                    check.detail += "; latest=" + tag;
                    if (!tag.equals("v" + currentVersion) && !tag.equals(currentVersion)) {
                        check.status = CheckStatus.WARNING;
                        check.summary = "Update available: " + tag + " (current: " + currentVersion + ")";
                        check.addIssue(new DoctorIssue("warning",
                            "A newer version is available",
                            currentVersion, tag,
                            "Run 'jwcode update' or re-run the installer: curl -fsSL https://.../install.sh | sh"));
                    }
                }
            }
        } catch (Exception ignored) {
            check.detail += "; update_check=failed";
        }
        check.durationMs = System.currentTimeMillis() - start;
        return check;
    }

    // ---- Legacy flat checks (keep backward compat) ----

    private CheckResult checkJava() {
        String v = System.getProperty("java.version");
        boolean ok = v != null && (v.startsWith("17") || v.startsWith("21") || v.startsWith("22") || v.startsWith("23"));
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

    // ---- helpers ----

    private String getVersion() {
        try {
            return DoctorService.class.getPackage().getImplementationVersion();
        } catch (Exception ignored) {
            return "dev";
        }
    }

    private static String extractJsonString(String json, String key) {
        // Minimal JSON string extraction without a full JSON parser
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) {
            search = "\"" + key + "\": \"";
            idx = json.indexOf(search);
        }
        if (idx < 0) return null;
        idx += search.length();
        int end = json.indexOf("\"", idx);
        if (end < 0) return null;
        return json.substring(idx, end);
    }
}
