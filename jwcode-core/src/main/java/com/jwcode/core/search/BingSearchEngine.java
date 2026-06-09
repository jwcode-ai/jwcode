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
 * Bing 搜索引擎实现
 * <p>
 * 使用 cn.bing.com HTML 接口进行搜索，国内可直接访问。
 * 使用 cn.bing.com HTML 接口进行搜索，国内可直接访问。
 *
 * @author JWCode Team
 * @since 1.0.0
 */
public class BingSearchEngine implements SearchEngine {

    private static final Logger logger = Logger.getLogger(BingSearchEngine.class.getName());

    private static final String BING_URL = "https://cn.bing.com/search?q=";

    private static final String DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private int connectTimeout = 10000;
    private int readTimeout = 10000;
    private String userAgent = DEFAULT_USER_AGENT;
    private boolean followRedirects = true;

    public BingSearchEngine() {
    }

    public BingSearchEngine(SearchEngineConfig config) {
        configure(config);
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        List<SearchResult> results = new ArrayList<>();

        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        HttpURLConnection conn = null;
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = BING_URL + encodedQuery + "&count=" + Math.min(maxResults, 50);

            URL url = new URL(searchUrl);
            conn = (HttpURLConnection) url.openConnection();
            setupConnection(conn);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                logger.warning("Bing 搜索请求失败，HTTP 状态码: " + responseCode);
                return results;
            }

            StringBuilder html = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    html.append(line).append("\n");
                }
            }

            results = parseResults(html.toString(), maxResults);

        } catch (Exception e) {
            logger.severe("Bing 搜索失败: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        return results;
    }

    @Override
    public String getName() {
        return "bing";
    }

    @Override
    public String getDisplayName() {
        return "Bing";
    }

    @Override
    public String getDescription() {
        return "Bing 搜索引擎 - 微软搜索服务，国内可直接访问";
    }

    @Override
    public boolean requiresApiKey() {
        return false;
    }

    @Override
    public void configure(SearchEngineConfig config) {
        if (config.getTimeoutMs() > 0) {
            this.connectTimeout = config.getTimeoutMs();
            this.readTimeout = config.getTimeoutMs();
        }
        if (config.getUserAgent() != null) {
            this.userAgent = config.getUserAgent();
        }
        this.followRedirects = config.isFollowRedirects();
    }

    public void setTimeout(int timeoutMs) {
        this.connectTimeout = timeoutMs;
        this.readTimeout = timeoutMs;
    }

    private void setupConnection(HttpURLConnection conn) {
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
        conn.setRequestProperty("DNT", "1");
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setInstanceFollowRedirects(followRedirects);
    }

    private List<SearchResult> parseResults(String html, int maxResults) {
        List<SearchResult> results = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(html);

            // Bing 主结果: #b_results 下的 .b_algo
            Elements algoElements = doc.select("#b_results .b_algo, .b_algo");

            for (Element algo : algoElements) {
                if (results.size() >= maxResults) break;

                Element titleLink = algo.selectFirst("h2 a, .b_title a");
                if (titleLink == null) continue;

                String title = titleLink.text().trim();
                String url = titleLink.attr("href");

                Element snippetEl = algo.selectFirst(".b_caption p, .b_snippet, .b_lineclamp2");
                String snippet = snippetEl != null ? snippetEl.text().trim() : "";

                SearchResult result = SearchResult.builder()
                    .title(title)
                    .url(url)
                    .snippet(snippet)
                    .source(getName())
                    .relevanceScore(0.6)
                    .build();

                if (result.isValid()) {
                    results.add(result);
                }
            }

            if (results.isEmpty()) {
                results = parseAlternative(doc, maxResults);
            }

        } catch (Exception e) {
            logger.warning("解析 Bing 搜索结果失败: " + e.getMessage());
        }

        return results;
    }

    private List<SearchResult> parseAlternative(Document doc, int maxResults) {
        List<SearchResult> results = new ArrayList<>();

        Elements links = doc.select("h2 a[href]");
        for (Element link : links) {
            if (results.size() >= maxResults) break;

            String href = link.attr("href");
            if (href.contains("bing.com") || href.startsWith("#") || href.isEmpty()) continue;

            String title = link.text().trim();
            if (title.length() < 3) continue;

            SearchResult result = SearchResult.builder()
                .title(title)
                .url(href)
                .snippet("")
                .source(getName())
                .relevanceScore(0.4)
                .build();

            if (result.isValid()) {
                results.add(result);
            }
        }

        return results;
    }
}
