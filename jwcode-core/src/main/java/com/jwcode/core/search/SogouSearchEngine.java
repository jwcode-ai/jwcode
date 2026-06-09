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
 * 搜狗搜索引擎实现
 * <p>
 * 使用搜狗 HTML 接口进行搜索，国内主流搜索引擎之一。
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class SogouSearchEngine implements SearchEngine {

    private static final Logger logger = Logger.getLogger(SogouSearchEngine.class.getName());
    private static final String SOGOU_URL = "https://www.sogou.com/web?query=";

    private static final String DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private int connectTimeout = 10000;
    private int readTimeout = 10000;
    private String userAgent = DEFAULT_USER_AGENT;
    private boolean followRedirects = true;

    public SogouSearchEngine() {}

    public SogouSearchEngine(SearchEngineConfig config) { configure(config); }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return results;

        HttpURLConnection conn = null;
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = SOGOU_URL + encodedQuery;

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
            logger.severe("搜狗搜索失败: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return results;
    }

    @Override public String getName() { return "sogou"; }
    @Override public String getDisplayName() { return "搜狗"; }
    @Override public String getDescription() { return "搜狗搜索引擎 - 国内主流搜索服务"; }
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
            Elements items = doc.select(".results .vrwrap, .results .rb, div.vrwrap");

            for (Element item : items) {
                if (results.size() >= maxResults) break;

                Element titleLink = item.selectFirst(".vr-title a, h3 a, .vrTitle a");
                if (titleLink == null) continue;

                String title = titleLink.text().trim();
                String url = titleLink.attr("href");

                Element snippetEl = item.selectFirst(".star-wiki, .str-text, .vr_summary, .space-txt, .str_info_div");
                String snippet = snippetEl != null ? snippetEl.text().trim() : "";

                results.add(SearchResult.builder()
                    .title(title).url(url).snippet(snippet)
                    .source(getName()).relevanceScore(0.5).build());
            }

            if (results.isEmpty()) {
                Elements links = doc.select("h3 a[href]");
                for (Element link : links) {
                    if (results.size() >= maxResults) break;
                    String href = link.attr("href");
                    if (href.contains("sogou.com") || href.startsWith("#") || href.isEmpty()) continue;
                    String title = link.text().trim();
                    if (title.length() < 3) continue;
                    results.add(SearchResult.builder()
                        .title(title).url(href).snippet("")
                        .source(getName()).relevanceScore(0.3).build());
                }
            }
        } catch (Exception e) {
            logger.warning("解析搜狗结果失败: " + e.getMessage());
        }
        return results;
    }
}
