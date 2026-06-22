package com.jwcode.core.tool.browser;

import com.microsoft.playwright.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * PlaywrightManager — 管理 Playwright 浏览器生命周期（单例）。
 *
 * <p>使用系统安装的 Chrome/Chromium 浏览器（通过 CHROME_PATH 环境变量或自动检测），
 * 不下载 Playwright 内置浏览器。</p>
 *
 * <p>支持的渲染模式：</p>
 * <ul>
 *   <li>{@code render(url)} — 渲染页面并返回完整 HTML、标题、元数据</li>
 *   <li>{@code navigate(url)} — 导航并返回页面摘要（a11y-like）</li>
 *   <li>{@code click(selector)} — 点击元素</li>
 *   <li>{@code type(selector, text)} — 输入文本</li>
 *   <li>{@code screenshot()} — 截图（base64）</li>
 *   <li>{@code snapshot()} — 获取页面无障碍树</li>
 * </ul>
 *
 * <p>线程安全：所有公开方法都是线程安全的。</p>
 */
public class PlaywrightManager {

    private static final Logger logger = Logger.getLogger(PlaywrightManager.class.getName());
    private static final PlaywrightManager INSTANCE = new PlaywrightManager();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 浏览器实例锁 + 状态
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean available = false;
    private volatile boolean initialized = false;
    private volatile boolean headless = true;

    // 运行时持有的 Playwright 对象（直接 API 类型）
    private Playwright playwrightInstance;
    private Browser browserInstance;
    private BrowserContext browserContextInstance;
    private final Map<Integer, Page> activePages = new ConcurrentHashMap<>();
    private int nextPageId = 1;

    private PlaywrightManager() {
    }

    public static PlaywrightManager getInstance() {
        return INSTANCE;
    }

    // ==================== 生命周期 ====================

    /**
     * 尝试初始化 Playwright。
     *
     * @return true 如果初始化成功
     */
    public boolean initialize() {
        if (initialized) return available;
        lock.lock();
        try {
            if (initialized) return available;
            available = tryInitializePlaywright();
            initialized = true;
            if (available) {
                logger.info("Playwright 浏览器引擎初始化成功（headless=" + headless + "）");
            } else {
                logger.severe("Playwright 浏览器引擎不可用。" +
                    "请确保已安装 Google Chrome 或 Chromium 浏览器，或通过 CHROME_PATH 环境变量指定路径。");
            }
        } catch (Exception e) {
            logger.warning("Playwright 初始化失败: " + e.getMessage());
            available = false;
            initialized = true;
        } finally {
            lock.unlock();
        }
        return available;
    }

    /**
     * 设置是否为无头模式（在调用 {@link #initialize()} 之前设置有效）。
     */
    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    /**
     * 检查 Playwright 是否可用。
     */
    public boolean isAvailable() {
        if (!initialized) initialize();
        return available;
    }

    /**
     * 获取引擎名称。
     */
    public String getName() {
        if (!initialized) initialize();
        return available ? "Playwright + 系统 Chrome" : "Playwright (不可用)";
    }

    /**
     * 关闭浏览器并释放资源。
     */
    public void shutdown() {
        lock.lock();
        try {
            if (browserInstance != null) {
                try { browserInstance.close(); } catch (Exception ignored) { }
                browserInstance = null;
            }
            if (playwrightInstance != null) {
                try { playwrightInstance.close(); } catch (Exception ignored) { }
                playwrightInstance = null;
            }
            browserContextInstance = null;
            available = false;
            initialized = false;
            activePages.clear();
            logger.info("Playwright 浏览器引擎已关闭");
        } finally {
            lock.unlock();
        }
    }

    // ==================== 页面操作 ====================

    /**
     * 渲染页面并返回完整内容。
     *
     * @param url    目标 URL
     * @param waitMs 等待页面加载的毫秒数（默认 3000）
     * @return 渲染结果，包含 HTML、标题、元数据
     */
    public RenderResult render(String url, int waitMs) {
        if (!ensureAvailable()) {
            return RenderResult.unavailable("Playwright 浏览器引擎不可用");
        }
        Objects.requireNonNull(url, "url 不能为空");

        long start = System.currentTimeMillis();
        lock.lock();
        try {
            Page page = createPage();
            try {
                page.navigate(url);
                sleep(Math.min(waitMs, 15000));

                RenderResult result = new RenderResult();
                result.success = true;
                result.html = page.content();
                result.title = page.title();
                result.contentType = "text/html";
                result.loadTimeMs = System.currentTimeMillis() - start;
                result.metadata = extractMetadata(page);

                return result;
            } finally {
                page.close();
            }
        } catch (Exception e) {
            logger.warning("渲染页面失败: " + url + " - " + e.getMessage());
            return RenderResult.error("渲染失败: " + e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    /**
     * 导航到 URL 并返回页面摘要。
     */
    public NavigationResult navigate(String url) {
        if (!ensureAvailable()) {
            return NavigationResult.unavailable();
        }
        Objects.requireNonNull(url, "url 不能为空");

        lock.lock();
        try {
            Page page = createPage();
            try {
                page.navigate(url);
                sleep(3000);

                int pageId = nextPageId++;
                activePages.put(pageId, page);

                NavigationResult result = new NavigationResult();
                result.success = true;
                result.title = page.title();
                result.pageContent = page.content();
                result.pageId = pageId;
                return result;
            } catch (Exception e) {
                page.close();
                throw e;
            }
        } catch (Exception e) {
            logger.warning("导航失败: " + url + " - " + e.getMessage());
            return NavigationResult.error(e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    /**
     * 在指定页面点击元素。
     */
    public ActionResult click(int pageId, String selector) {
        return withPage(pageId, page -> {
            page.click(selector);
            sleep(500);
            return ActionResult.success("已点击: " + selector);
        });
    }

    /**
     * 在指定页面输入文本。
     */
    public ActionResult type(int pageId, String selector, String text) {
        return withPage(pageId, page -> {
            page.fill(selector, text);
            return ActionResult.success("已输入: " + text + " 到: " + selector);
        });
    }

    /**
     * 滚动页面。
     */
    public ActionResult scroll(int pageId, int x, int y) {
        return withPage(pageId, page -> {
            page.evaluate("window.scrollTo(" + x + ", " + y + ")");
            return ActionResult.success("已滚动到: (" + x + ", " + y + ")");
        });
    }

    /**
     * 获取页面无障碍快照。
     */
    public SnapshotResult snapshot(int pageId) {
        return withPage(pageId, page -> {
            String title = page.title();
            String content = page.content();
            return buildSnapshotResult(title, content);
        });
    }

    /**
     * 截取页面截图（base64 PNG）。
     */
    public ScreenshotResult screenshot(int pageId) {
        return withPage(pageId, page -> {
            try {
                Page.ScreenshotOptions opts = new Page.ScreenshotOptions();
                byte[] bytes = page.screenshot(opts);
                String base64 = Base64.getEncoder().encodeToString(bytes);
                ScreenshotResult result = new ScreenshotResult();
                result.success = true;
                result.base64Data = base64;
                result.mimeType = "image/png";
                return result;
            } catch (Exception e) {
                logger.warning("截图失败: " + e.getMessage());
                ScreenshotResult result = new ScreenshotResult();
                result.success = false;
                result.error = "截图失败: " + e.getMessage();
                return result;
            }
        });
    }

    /**
     * 等待指定毫秒。
     */
    public ActionResult waitFor(int ms) {
        int actualMs = Math.min(ms, 10000);
        sleep(actualMs);
        return ActionResult.success("已等待 " + actualMs + "ms");
    }

    // ==================== 内部实现 ====================

    private boolean ensureAvailable() {
        if (!initialized) initialize();
        return available;
    }

    private boolean tryInitializePlaywright() {
        try {
            // 通过 CreateOptions.setEnv 跳过浏览器自动下载
            Map<String, String> envOverrides = new HashMap<>();
            envOverrides.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
            Playwright.CreateOptions options = new Playwright.CreateOptions()
                .setEnv(envOverrides);

            this.playwrightInstance = Playwright.create(options);
            logger.info("Playwright 浏览器自动下载已跳过，将使用系统 Chrome");

            // 检测系统 Chrome/Chromium
            Path chromePath = detectSystemChrome();
            if (chromePath == null) {
                logger.severe("未检测到系统 Chrome/Chromium 浏览器。请安装 Google Chrome 或 Chromium，"
                    + "或通过 CHROME_PATH 环境变量指定路径。");
                this.playwrightInstance.close();
                this.playwrightInstance = null;
                return false;
            }
            logger.info("使用系统 Chrome: " + chromePath);

            // 配置 launch options
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setExecutablePath(chromePath);

            // 启动浏览器
            this.browserInstance = playwrightInstance.chromium().launch(launchOptions);
            this.browserContextInstance = browserInstance.newContext();

            return true;
        } catch (NoClassDefFoundError e) {
            logger.info("Playwright 依赖未找到: " + e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warning("Playwright 初始化失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检测系统中已安装的 Chrome/Chromium 浏览器路径。
     * 优先级：CHROME_PATH 环境变量 > PLAYWRIGHT_CHROME_EXECUTABLE > 常见安装路径
     *
     * @return 浏览器可执行文件路径，未找到返回 null
     */
    private Path detectSystemChrome() {
        // 1. 环境变量（用户显式指定）
        String envPath = System.getenv("CHROME_PATH");
        if (envPath != null && !envPath.isEmpty()) {
            Path path = Paths.get(envPath);
            if (Files.exists(path)) {
                logger.info("通过 CHROME_PATH 环境变量找到 Chrome: " + envPath);
                return path;
            }
            logger.warning("CHROME_PATH 环境变量指定的路径不存在: " + envPath);
        }

        envPath = System.getenv("PLAYWRIGHT_CHROME_EXECUTABLE");
        if (envPath != null && !envPath.isEmpty()) {
            Path path = Paths.get(envPath);
            if (Files.exists(path)) {
                logger.info("通过 PLAYWRIGHT_CHROME_EXECUTABLE 环境变量找到 Chrome: " + envPath);
                return path;
            }
        }

        // 2. 按操作系统检测常见安装路径
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (os.contains("win")) {
            // Windows
            String localAppData = System.getenv("LOCALAPPDATA");
            String programFiles = System.getenv("ProgramFiles");
            String programFilesX86 = System.getenv("ProgramFiles(x86)");

            String[] paths = {
                programFiles + "\\Google\\Chrome\\Application\\chrome.exe",
                programFilesX86 + "\\Google\\Chrome\\Application\\chrome.exe",
                localAppData + "\\Google\\Chrome\\Application\\chrome.exe",
                localAppData + "\\Chromium\\Application\\chrome.exe",
                System.getProperty("user.home") + "\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe"
            };
            for (String p : paths) {
                if (p != null) {
                    Path path = Paths.get(p);
                    if (Files.exists(path)) return path;
                }
            }
        } else if (os.contains("mac") || os.contains("darwin")) {
            // macOS
            String[] paths = {
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Chromium.app/Contents/MacOS/Chromium"
            };
            for (String p : paths) {
                Path path = Paths.get(p);
                if (Files.exists(path)) return path;
            }
        } else {
            // Linux: 从 PATH 中查找
            String pathEnv = System.getenv("PATH");
            if (pathEnv != null) {
                String[] dirs = pathEnv.split(File.pathSeparator);
                String[] executables = {
                    "google-chrome", "google-chrome-stable",
                    "chromium", "chromium-browser", "chromium/chrome"
                };
                for (String dir : dirs) {
                    for (String exe : executables) {
                        Path path = Paths.get(dir, exe);
                        if (Files.exists(path) && Files.isExecutable(path)) {
                            return path.toAbsolutePath();
                        }
                    }
                }
            }
        }

        return null;
    }

    private Page createPage() {
        return browserContextInstance.newPage();
    }

    /**
     * 提取页面元数据（OG 标签、meta 描述等）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractMetadata(Page page) {
        Map<String, String> meta = new LinkedHashMap<>();
        try {
            String js = """
                JSON.stringify({
                    'description': (document.querySelector('meta[name="description"]')?.content || ''),
                    'keywords': (document.querySelector('meta[name="keywords"]')?.content || ''),
                    'og:title': (document.querySelector('meta[property="og:title"]')?.content || ''),
                    'og:description': (document.querySelector('meta[property="og:description"]')?.content || ''),
                    'og:image': (document.querySelector('meta[property="og:image"]')?.content || ''),
                    'og:url': (document.querySelector('meta[property="og:url"]')?.content || '')
                })
                """;
            String result = page.evaluate(js).toString();
            if (result != null && !result.isEmpty()) {
                Map<String, String> parsed = MAPPER.readValue(result, Map.class);
                meta.putAll(parsed);
                meta.values().removeIf(v -> v == null || v.isEmpty());
            }
        } catch (Exception ignored) {
        }
        return meta;
    }

    /**
     * 构建页面快照结果。
     */
    private SnapshotResult buildSnapshotResult(String title, String html) {
        SnapshotResult result = new SnapshotResult();
        result.success = true;
        result.title = title;

        StringBuilder sb = new StringBuilder();
        sb.append("=== Accessibility Snapshot ===\n");
        sb.append("Title: ").append(title).append("\n\n");

        // 提取标题
        try {
            var m = java.util.regex.Pattern
                .compile("<(h[1-6])[^>]*>([^<]*)</\\1>", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(html);
            int count = 0;
            while (m.find() && count < 50) {
                String level = m.group(1).toLowerCase();
                String text = m.group(2).trim();
                if (!text.isEmpty()) {
                    sb.append("  ").append(level).append(": ").append(text).append("\n");
                    count++;
                }
            }
            sb.append("\n");
        } catch (Exception ignored) {
        }

        // 提取链接
        try {
            var m = java.util.regex.Pattern
                .compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>([^<]*)</a>",
                    java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(html);
            int count = 0;
            while (m.find() && count < 30) {
                String href = m.group(1).trim();
                String label = m.group(2).trim().replaceAll("\\s+", " ");
                if (!href.startsWith("javascript:") && !href.startsWith("#")) {
                    sb.append("  [").append(++count).append("] ");
                    if (!label.isEmpty()) sb.append("\"").append(label).append("\" -> ");
                    sb.append(href).append("\n");
                }
            }
        } catch (Exception ignored) {
        }

        result.snapshot = sb.toString();
        return result;
    }

    /**
     * 在页面上执行操作的辅助方法。
     */
    private <T> T withPage(int pageId, PageOperation<T> op) {
        Page page = activePages.get(pageId);
        if (page == null) {
            return op.onError("页面 " + pageId + " 未找到或已关闭");
        }
        try {
            return op.execute(page);
        } catch (Exception e) {
            return op.onError("操作失败: " + e.getMessage());
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 内部类定义 ====================

    @FunctionalInterface
    private interface PageOperation<T> {
        T execute(Page page) throws Exception;

        default T onError(String message) {
            return null;
        }
    }

    // ==================== 结果类型 ====================

    /**
     * 页面渲染结果。
     */
    public static class RenderResult {
        public boolean success;
        public String html;
        public String title;
        public String contentType;
        public String error;
        public long loadTimeMs;
        public Map<String, String> metadata;

        static RenderResult unavailable(String msg) {
            RenderResult r = new RenderResult();
            r.success = false;
            r.error = msg;
            return r;
        }

        static RenderResult error(String msg) {
            RenderResult r = new RenderResult();
            r.success = false;
            r.error = msg;
            return r;
        }
    }

    /**
     * 导航结果。
     */
    public static class NavigationResult {
        public boolean success;
        public String title;
        public String pageContent;
        public int pageId;
        public String error;

        static NavigationResult unavailable() {
            NavigationResult r = new NavigationResult();
            r.success = false;
            r.error = "Playwright 浏览器引擎不可用";
            return r;
        }

        static NavigationResult error(String msg) {
            NavigationResult r = new NavigationResult();
            r.success = false;
            r.error = msg;
            return r;
        }
    }

    /**
     * 操作结果。
     */
    public static class ActionResult {
        public boolean success = true;
        public String message;
        public String error;

        static ActionResult success(String msg) {
            ActionResult r = new ActionResult();
            r.message = msg;
            return r;
        }

        static ActionResult error(String msg) {
            ActionResult r = new ActionResult();
            r.success = false;
            r.error = msg;
            return r;
        }
    }

    /**
     * 快照结果。
     */
    public static class SnapshotResult {
        public boolean success;
        public String title;
        public String snapshot;
        public String error;
    }

    /**
     * 截图结果。
     */
    public static class ScreenshotResult {
        public boolean success;
        public String base64Data;
        public String mimeType;
        public String error;
    }
}
