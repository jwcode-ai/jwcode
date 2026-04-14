package com.jwcode.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * AutoUpdateService - 自动更新服务
 *
 * 功能说明：
 * 检查并安装 JWCode 更新，支持版本比较和自动下载。
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class AutoUpdateService {

    private static final String DEFAULT_UPDATE_URL = "https://api.github.com/repos/your-org/jwcode/releases/latest";
    private static final String DEFAULT_VERSION = "1.0.0";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ExecutorService executor;
    private final String currentVersion;
    private final String updateUrl;
    private final Path installDirectory;

    private volatile UpdateStatus lastCheckStatus;
    private volatile boolean isUpdating;
    private volatile String latestVersionJson;

    public AutoUpdateService() {
        this(DEFAULT_VERSION, DEFAULT_UPDATE_URL, null);
    }

    public AutoUpdateService(String currentVersion, String updateUrl, String installDirectory) {
        this.executor = Executors.newSingleThreadExecutor();
        this.currentVersion = currentVersion;
        this.updateUrl = updateUrl;
        this.installDirectory = installDirectory != null
                ? Paths.get(installDirectory)
                : Paths.get(System.getProperty("user.home"), ".jwcode");
        this.lastCheckStatus = null;
        this.isUpdating = false;
        this.latestVersionJson = null;
    }

    /**
     * 检查更新
     */
    public CompletableFuture<UpdateStatus> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String json = fetchLatestVersion();
                latestVersionJson = json;
                String latestVersion = parseVersion(json);
                String releaseNotes = parseReleaseNotes(json);

                boolean hasUpdate = compareVersions(currentVersion, latestVersion) < 0;

                UpdateStatus status = new UpdateStatus();
                status.currentVersion = currentVersion;
                status.latestVersion = latestVersion;
                status.hasUpdate = hasUpdate;
                status.releaseNotes = releaseNotes;
                status.checkedAt = Instant.now().toString();

                lastCheckStatus = status;
                return status;

            } catch (Exception e) {
                UpdateStatus errorStatus = new UpdateStatus();
                errorStatus.currentVersion = currentVersion;
                errorStatus.error = "检查更新失败：" + e.getMessage();
                errorStatus.checkedAt = Instant.now().toString();
                return errorStatus;
            }
        }, executor);
    }

    /**
     * 下载并安装更新
     */
    public CompletableFuture<UpdateResult> installUpdate(Consumer<Double> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            if (isUpdating) {
                return new UpdateResult(false, "更新正在进行中");
            }

            if (lastCheckStatus == null || !lastCheckStatus.hasUpdate) {
                return new UpdateResult(false, "没有可用更新");
            }

            isUpdating = true;
            try {
                // 步骤 1: 下载更新包
                if (progressCallback != null) {
                    progressCallback.accept(0.1);
                }

                String downloadUrl = parseDownloadUrl(latestVersionJson);
                Path downloadPath = downloadUpdate(downloadUrl, progressCallback);

                // 步骤 2: 验证下载
                if (progressCallback != null) {
                    progressCallback.accept(0.7);
                }

                boolean verified = verifyDownload(downloadPath, downloadUrl);
                if (!verified) {
                    return new UpdateResult(false, "更新包验证失败");
                }

                // 步骤 3: 安装更新
                if (progressCallback != null) {
                    progressCallback.accept(0.8);
                }

                installDownload(downloadPath, lastCheckStatus.latestVersion);

                // 步骤 4: 清理
                Files.deleteIfExists(downloadPath);

                isUpdating = false;
                return new UpdateResult(true, "更新安装成功");

            } catch (Exception e) {
                isUpdating = false;
                return new UpdateResult(false, "更新失败：" + e.getMessage());
            }
        }, executor);
    }

    /**
     * 应用在启动时检测并执行待处理的更新
     */
    public UpdateResult applyPendingUpdate() {
        Path marker = installDirectory.resolve(".update_pending");
        if (!Files.exists(marker)) {
            return new UpdateResult(false, "没有待应用的更新");
        }

        try {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(marker)) {
                props.load(in);
            }

            Path updateDir = Paths.get(props.getProperty("extractedPath", installDirectory.resolve("update").toString()));
            if (!Files.exists(updateDir)) {
                Files.deleteIfExists(marker);
                return new UpdateResult(false, "更新目录不存在");
            }

            try (Stream<Path> walk = Files.walk(updateDir)) {
                List<Path> sources = walk.filter(p -> !p.equals(updateDir)).sorted().toList();
                for (Path source : sources) {
                    Path target = installDirectory.resolve(updateDir.relativize(source));
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            deleteDirectory(updateDir);
            Files.deleteIfExists(marker);

            return new UpdateResult(true, "更新应用成功");
        } catch (Exception e) {
            return new UpdateResult(false, "应用更新失败：" + e.getMessage());
        }
    }

    /**
     * 获取最新版本信息
     */
    private String fetchLatestVersion() throws IOException {
        URL url = new URL(updateUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "JWCode-AutoUpdate/1.0");

        try {
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP 错误：" + responseCode);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            return response.toString();
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 解析版本号
     */
    private String parseVersion(String json) throws IOException {
        Map<String, Object> map = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        Object tagName = map.get("tag_name");
        return tagName != null ? tagName.toString() : DEFAULT_VERSION;
    }

    /**
     * 解析发布说明
     */
    private String parseReleaseNotes(String json) throws IOException {
        Map<String, Object> map = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        Object body = map.get("body");
        return body != null ? body.toString() : "";
    }

    /**
     * 解析下载 URL（优先从 GitHub assets 中获取 browser_download_url）
     */
    private String parseDownloadUrl(String json) {
        if (json == null || json.isBlank()) {
            return fallbackDownloadUrl(DEFAULT_VERSION);
        }
        try {
            Map<String, Object> map = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> assets = (List<Map<String, Object>>) map.get("assets");
            if (assets != null) {
                for (Map<String, Object> asset : assets) {
                    Object url = asset.get("browser_download_url");
                    if (url != null) {
                        return url.toString();
                    }
                }
            }
            Object tagName = map.get("tag_name");
            if (tagName != null) {
                return fallbackDownloadUrl(tagName.toString());
            }
        } catch (Exception e) {
            // fallback
        }
        return fallbackDownloadUrl(DEFAULT_VERSION);
    }

    private String fallbackDownloadUrl(String version) {
        return "https://github.com/your-org/jwcode/releases/download/" + version + "/jwcode-" + version + ".zip";
    }

    /**
     * 下载更新包
     */
    private Path downloadUpdate(String url, Consumer<Double> progressCallback) throws IOException {
        Path downloadPath = installDirectory.resolve("update.zip");
        Files.createDirectories(downloadPath.getParent());

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "JWCode-AutoUpdate/1.0");

        int contentLength = conn.getContentLength();
        long downloadedBytes = 0;

        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(downloadPath)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloadedBytes += read;
                if (progressCallback != null && contentLength > 0) {
                    double progress = 0.1 + (0.6 * (double) downloadedBytes / contentLength);
                    progressCallback.accept(progress);
                }
            }
        } finally {
            conn.disconnect();
        }

        if (progressCallback != null) {
            progressCallback.accept(0.7);
        }

        return downloadPath;
    }

    /**
     * 验证下载（通过 companion .sha256 文件校验）
     */
    private boolean verifyDownload(Path downloadPath, String downloadUrl) throws IOException {
        String sha256Url = downloadUrl + ".sha256";
        HttpURLConnection conn = (HttpURLConnection) new URL(sha256Url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "JWCode-AutoUpdate/1.0");

        String expectedHash;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            expectedHash = reader.readLine();
        } finally {
            conn.disconnect();
        }

        if (expectedHash == null || expectedHash.isBlank()) {
            return false;
        }

        expectedHash = expectedHash.trim().split("\s+")[0];
        String actualHash = calculateSha256(downloadPath);
        return expectedHash.equalsIgnoreCase(actualHash);
    }

    /**
     * 计算文件的 SHA-256 值
     */
    private String calculateSha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 安装下载的更新（解压并写入 pending marker）
     */
    private void installDownload(Path downloadPath, String version) throws IOException {
        Path updateDir = installDirectory.resolve("update");
        if (Files.exists(updateDir)) {
            deleteDirectory(updateDir);
        }
        Files.createDirectories(updateDir);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(downloadPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = updateDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(updateDir)) {
                    throw new IOException("非法 ZIP 条目: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }

        Properties props = new Properties();
        props.setProperty("version", version);
        props.setProperty("extractedPath", updateDir.toString());
        props.setProperty("timestamp", Instant.now().toString());

        Path marker = installDirectory.resolve(".update_pending");
        try (OutputStream out = Files.newOutputStream(marker)) {
            props.store(out, "Pending update marker");
        }
    }

    private void deleteDirectory(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    // ignore
                }
            });
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * 比较版本号
     */
    public static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.replaceAll("[^0-9.]", "").split("\\.");
        String[] parts2 = v2.replaceAll("[^0-9.]", "").split("\\.");

        int maxLen = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLen; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

            if (num1 < num2) return -1;
            if (num1 > num2) return 1;
        }

        return 0;
    }

    /**
     * 获取当前版本
     */
    public String getCurrentVersion() {
        return currentVersion;
    }

    /**
     * 是否正在更新
     */
    public boolean isUpdating() {
        return isUpdating;
    }

    /**
     * 获取上次检查状态
     */
    public UpdateStatus getLastCheckStatus() {
        return lastCheckStatus;
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * 更新状态
     */
    public static class UpdateStatus {
        public String currentVersion;
        public String latestVersion;
        public boolean hasUpdate;
        public String releaseNotes;
        public String error;
        public String checkedAt;

        public Map<String, Object> toMap() {
            return Map.of(
                    "currentVersion", currentVersion,
                    "latestVersion", latestVersion != null ? latestVersion : "",
                    "hasUpdate", hasUpdate,
                    "releaseNotes", releaseNotes != null ? releaseNotes : "",
                    "error", error != null ? error : "",
                    "checkedAt", checkedAt
            );
        }
    }

    /**
     * 更新结果
     */
    public static class UpdateResult {
        public final boolean success;
        public final String message;

        public UpdateResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public Map<String, Object> toMap() {
            return Map.of(
                    "success", success,
                    "message", message
            );
        }
    }
}
