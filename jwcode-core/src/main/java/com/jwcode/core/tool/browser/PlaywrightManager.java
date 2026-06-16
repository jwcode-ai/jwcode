package com.jwcode.core.tool.browser;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * PlaywrightManager — 管理 Playwright 浏览器生命周期（单例）。
 *
 * <p>提供对基于 Playwright 的 Chromium 浏览器的延迟初始化、页面渲染和浏览器自动化操作。
 * 当 Playwright 库不在类路径上时，所有操作会优雅降级并返回清晰的错误信息。</p>
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

    // Playwright 类全名（用于反射检测）
    private static final String PLAYWRIGHT_CLASS = "com.microsoft.playwright.Playwright";
    private static final String BROWSER_CLASS = "com.microsoft.playwright.Browser";
    private static final String PAGE_CLASS = "com.microsoft.playwright.Page";

    // 浏览器实例锁 + 状态
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean available = false;
    private volatile boolean initialized = false;
    private volatile boolean headless = true;

    // 运行时持有的 Playwright 对象（通过 Object 引用避免直接编译依赖）
    private Object playwrightInstance;
    private Object browserInstance;
    private Object browserContextInstance;
    private final Map<Integer, Object> activePages = new ConcurrentHashMap<>();
    private int nextPageId = 1;

    private PlaywrightManager() {
    }

    public static PlaywrightManager getInstance() {
        return INSTANCE;
    }

    // ==================== 生命周期 ====================

    /**
     * 尝试初始化 Playwright。
     * 如果 Playwright 不在类路径上，则静默标记为不可用。
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
                logger.warning("Playwright 不可用，浏览器渲染功能将受限。可通过添加依赖启用：com.microsoft.playwright:playwright");
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
        return available ? "Playwright (Chromium)" : "Playwright (不可用)";
    }

    /**
     * 关闭浏览器并释放资源。
     */
    public void shutdown() {
        lock.lock();
        try {
            closeBrowser();
            closePlaywright();
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
            Object page = createPage();
            try {
                invoke(page, "navigate", url);
                sleep(Math.min(waitMs, 15000));

                String html = (String) invoke(page, "content");
                String title = (String) invoke(page, "title");

                RenderResult result = new RenderResult();
                result.success = true;
                result.html = html;
                result.title = title;
                result.contentType = "text/html";
                result.loadTimeMs = System.currentTimeMillis() - start;
                result.metadata = extractMetadata(page);

                return result;
            } finally {
                closePage(page);
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
            Object page = createPage();
            try {
                invoke(page, "navigate", url);
                sleep(3000);

                String title = (String) invoke(page, "title");
                String content = (String) invoke(page, "content");
                int pageId = nextPageId++;
                activePages.put(pageId, page);

                NavigationResult result = new NavigationResult();
                result.success = true;
                result.title = title;
                result.pageContent = content;
                result.pageId = pageId;
                return result;
            } catch (Exception e) {
                closePage(page);
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
            invoke(page, "click", selector);
            sleep(500);
            return ActionResult.success("已点击: " + selector);
        });
    }

    /**
     * 在指定页面输入文本。
     */
    public ActionResult type(int pageId, String selector, String text) {
        return withPage(pageId, page -> {
            invoke(page, "fill", selector, text);
            return ActionResult.success("已输入: " + text + " 到: " + selector);
        });
    }

    /**
     * 滚动页面。
     */
    public ActionResult scroll(int pageId, int x, int y) {
        return withPage(pageId, page -> {
            invoke(page, "evaluate", "window.scrollTo(" + x + ", " + y + ")");
            return ActionResult.success("已滚动到: (" + x + ", " + y + ")");
        });
    }

    /**
     * 获取页面无障碍快照。
     */
    public SnapshotResult snapshot(int pageId) {
        return withPage(pageId, page -> {
            String title = (String) invoke(page, "title");
            String content = (String) invoke(page, "content");
            return buildSnapshotResult(title, content);
        });
    }

    /**
     * 截取页面截图（base64 PNG）。
     */
    public ScreenshotResult screenshot(int pageId) {
        return withPage(pageId, page -> {
            try {
                // page.screenshot() — 返回 byte[] (PNG)
                byte[] bytes = (byte[]) invoke(page, "screenshot");
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

    @SuppressWarnings("unchecked")
    private boolean tryInitializePlaywright() {
        try {
            // 使用反射检测 Playwright 类
            Class<?> playwrightClass = Class.forName(PLAYWRIGHT_CLASS);

            // 调用 Playwright.create()
            Object p = playwrightClass.getMethod("create").invoke(null);
            this.playwrightInstance = p;

            // 配置 launch options
            Class<?> launchOptionsClass = Class.forName("com.microsoft.playwright.BrowserType$LaunchOptions");
            Object launchOptions = launchOptionsClass.getDeclaredConstructor().newInstance();
            launchOptionsClass.getMethod("setHeadless", boolean.class).invoke(launchOptions, headless);

            // 获取 Chromium 浏览器类型
            Object chromium = p.getClass().getMethod("chromium").invoke(p);
            Object browser = chromium.getClass()
                .getMethod("launch", launchOptionsClass)
                .invoke(chromium, launchOptions);
            this.browserInstance = browser;

            // 创建 BrowserContext
            Object context = browser.getClass().getMethod("newContext").invoke(browser);
            this.browserContextInstance = context;

            return true;
        } catch (ClassNotFoundException e) {
            logger.info("Playwright 未在类路径中找到: " + e.getMessage());
            return false;
        } catch (NoSuchMethodException e) {
            logger.warning("Playwright API 版本不兼容: " + e.getMessage());
            return false;
        } catch (Exception e) {
            // Playwright 可能未安装浏览器二进制文件
            if (e.getMessage() != null && e.getMessage().contains("Executable doesn't exist")) {
                logger.warning("Playwright 浏览器二进制文件未安装。运行 'playwright install chromium' 或 'mvn exec:java -Dexec.mainClass=\"com.microsoft.playwright.CLI\" -Dexec.args=\"install chromium\"'");
            } else {
                logger.warning("Playwright 初始化失败: " + e.getMessage());
            }
            return false;
        }
    }

    private Object createPage() throws Exception {
        // browserContext.newPage()
        return browserContextInstance.getClass()
            .getMethod("newPage")
            .invoke(browserContextInstance);
    }

    private void closePage(Object page) {
        try {
            page.getClass().getMethod("close").invoke(page);
        } catch (Exception ignored) {
        }
    }

    private void closeBrowser() {
        if (browserInstance != null) {
            try {
                browserInstance.getClass().getMethod("close").invoke(browserInstance);
            } catch (Exception ignored) {
            }
            browserInstance = null;
        }
    }

    private void closePlaywright() {
        if (playwrightInstance != null) {
            try {
                playwrightInstance.getClass().getMethod("close").invoke(playwrightInstance);
            } catch (Exception ignored) {
            }
            playwrightInstance = null;
        }
        browserContextInstance = null;
    }

    /**
     * 通过反射调用对象方法。
     */
    private Object invoke(Object obj, String methodName, Object... args) throws Exception {
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i].getClass();
        }
        // 处理基本类型重载：String 参数是最常见的 Playwright API 形式
        try {
            return obj.getClass().getMethod(methodName, paramTypes).invoke(obj, args);
        } catch (NoSuchMethodException e) {
            // 回退：尝试将 String 参数匹配为 CharSequence
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof String) {
                    paramTypes[i] = CharSequence.class;
                }
            }
            return obj.getClass().getMethod(methodName, paramTypes).invoke(obj, args);
        }
    }

    /**
     * 提取页面元数据（OG 标签、meta 描述等）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractMetadata(Object page) {
        Map<String, String> meta = new LinkedHashMap<>();
        try {
            // 通过 JS eval 提取元数据
            String js = """
                JSON.stringify({
                    'description': (document.querySelector('meta[name=\"description\"]')?.content || ''),
                    'keywords': (document.querySelector('meta[name=\"keywords\"]')?.content || ''),
                    'og:title': (document.querySelector('meta[property=\"og:title\"]')?.content || ''),
                    'og:description': (document.querySelector('meta[property=\"og:description\"]')?.content || ''),
                    'og:image': (document.querySelector('meta[property=\"og:image\"]')?.content || ''),
                    'og:url': (document.querySelector('meta[property=\"og:url\"]')?.content || '')
                })
                """;
            String result = (String) invoke(page, "evaluate", js);
            if (result != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, String> parsed = mapper.readValue(result, Map.class);
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
    @SuppressWarnings("unchecked")
    private SnapshotResult buildSnapshotResult(String title, String html) {
        SnapshotResult result = new SnapshotResult();
        result.success = true;
        result.title = title;

        StringBuilder sb = new StringBuilder();
        sb.append("=== Accessibility Snapshot ===\n");
        sb.append("Title: ").append(title).append("\n\n");

        // 提取标题
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
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
            java.util.regex.Matcher m = java.util.regex.Pattern
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
        Object page = activePages.get(pageId);
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
        T execute(Object page) throws Exception;

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
