package com.jwcode.core.search;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 百度搜索引擎实现
 * <p>
 * 使用百度 HTML 接口进行搜索，国内首选搜索引擎。
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class BaiduSearchEngine implements SearchEngine {

    private static final Logger logger = Logger.getLogger(BaiduSearchEngine.class.getName());
    private static final String BAIDU_URL = "https://www.baidu.com/s?wd=";

    private static final String DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private int connectTimeout = 10000;
    private int readTimeout = 10000;
    private String userAgent = DEFAULT_USER_AGENT;
    private boolean followRedirects = true;

    public BaiduSearchEngine() {}

    public BaiduSearchEngine(SearchEngineConfig config) { configure(config); }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return results;

        HttpURLConnection conn = null;
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = BAIDU_URL + encodedQuery + "&rn=" + Math.min(maxResults, 50);

            URL url = new URL(searchUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", userAgent);
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setInstanceFollowRedirects(followRedirects);

            if (conn.getResponseCode() != 200) return results;

            StringBuilder html = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) html.append(line).append("\n");
            }

            results = parseResults(html.toString(), maxResults);
        } catch (Exception e) {
            logger.severe("百度搜索失败: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return results;
    }

    @Override public String getName() { return "baidu"; }
    @Override public String getDisplayName() { return "百度"; }
    @Override public String getDescription() { return "百度搜索引擎 - 国内首选，中文搜索效果最佳"; }
    @Override public boolean requiresApiKey() { return false; }

    @Override
    public void configure(SearchEngineConfig config) {
        if (config.getTimeoutMs() > 0) {
            this.connectTimeout = config.getTimeoutMs();
            this.readTimeout = config.getTimeoutMs();
        }
        if (config.getUserAgent() != null) this.userAgent = config.getUserAgent();
        this.followRedirects = config.isFollowRedirects();
    }

    private List<SearchResult> parseResults(String html, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(html);
            Elements resultDivs = doc.select(".result, .c-container");

            for (Element div : resultDivs) {
                if (results.size() >= maxResults) break;
                if (div.hasClass("c-result-card")) continue;

                Element titleLink = div.selectFirst("h3 a, .t a");
                if (titleLink == null) continue;

                String title = titleLink.text().trim();
                String url = titleLink.attr("href");

                Element snippetEl = div.selectFirst(".c-abstract, .c-span-last, span.content-right_8Zs40");
                String snippet = snippetEl != null ? snippetEl.text().trim() : "";

                results.add(SearchResult.builder()
                    .title(title).url(url).snippet(snippet)
                    .source(getName()).relevanceScore(0.7).build());
            }

            if (results.isEmpty()) {
                Elements h3Links = doc.select("h3 a[href]");
                for (Element link : h3Links) {
                    if (results.size() >= maxResults) break;
                    String href = link.attr("href");
                    if (href.contains("baidu.com") || href.startsWith("#") || href.isEmpty()) continue;
                    String title = link.text().trim();
                    if (title.length() < 3) continue;
                    results.add(SearchResult.builder()
                        .title(title).url(href).snippet("")
                        .source(getName()).relevanceScore(0.4).build());
                }
            }
        } catch (Exception e) {
            logger.warning("解析百度结果失败: " + e.getMessage());
        }
        return results;
    }
}
