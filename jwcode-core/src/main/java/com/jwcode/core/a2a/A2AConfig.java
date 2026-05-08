package com.jwcode.core.a2a;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * A2AConfig — A2A 协议配置类。
 *
 * <p>管理 A2A 调度模式、注册中心地址、Agent 端口等配置。
 * 支持从配置文件加载，也支持编程式设置。</p>
 */
public class A2AConfig {

    private static final Logger logger = Logger.getLogger(A2AConfig.class.getName());

    /** 调度模式 */
    public enum DispatchMode {
        /** 仅本地调度（默认） */
        LOCAL,
        /** 仅远程 A2A 调度 */
        REMOTE,
        /** 自动模式：优先远程，不可用时回退本地 */
        AUTO
    }

    private DispatchMode mode = DispatchMode.LOCAL;
    private String registryEndpoint = "http://localhost:9100";
    private int basePort = 9100;
    private int connectTimeoutSeconds = 10;
    private int requestTimeoutSeconds = 30;
    private boolean autoRefreshCards = true;
    private int refreshIntervalSeconds = 60;

    private static final String CONFIG_FILE = "a2a-config.properties";

    public A2AConfig() {
        loadFromProperties();
    }

    /**
     * 从配置文件加载
     */
    private void loadFromProperties() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is != null) {
                props.load(is);
                this.mode = DispatchMode.valueOf(
                    props.getProperty("a2a.mode", "LOCAL").toUpperCase());
                this.registryEndpoint = props.getProperty("a2a.registry.endpoint", registryEndpoint);
                this.basePort = Integer.parseInt(props.getProperty("a2a.base.port", String.valueOf(basePort)));
                this.connectTimeoutSeconds = Integer.parseInt(
                    props.getProperty("a2a.connect.timeout", String.valueOf(connectTimeoutSeconds)));
                this.requestTimeoutSeconds = Integer.parseInt(
                    props.getProperty("a2a.request.timeout", String.valueOf(requestTimeoutSeconds)));
                this.autoRefreshCards = Boolean.parseBoolean(
                    props.getProperty("a2a.auto.refresh", String.valueOf(autoRefreshCards)));
                this.refreshIntervalSeconds = Integer.parseInt(
                    props.getProperty("a2a.refresh.interval", String.valueOf(refreshIntervalSeconds)));
                logger.info("A2AConfig: loaded from " + CONFIG_FILE);
            }
        } catch (Exception e) {
            logger.fine("A2AConfig: no config file found, using defaults");
        }
    }

    // ==================== Getters & Setters ====================

    public DispatchMode getMode() { return mode; }
    public void setMode(DispatchMode mode) { this.mode = mode; }

    public String getRegistryEndpoint() { return registryEndpoint; }
    public void setRegistryEndpoint(String registryEndpoint) { this.registryEndpoint = registryEndpoint; }

    public int getBasePort() { return basePort; }
    public void setBasePort(int basePort) { this.basePort = basePort; }

    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }

    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }

    public boolean isAutoRefreshCards() { return autoRefreshCards; }
    public void setAutoRefreshCards(boolean autoRefreshCards) { this.autoRefreshCards = autoRefreshCards; }

    public int getRefreshIntervalSeconds() { return refreshIntervalSeconds; }
    public void setRefreshIntervalSeconds(int refreshIntervalSeconds) { this.refreshIntervalSeconds = refreshIntervalSeconds; }

    /**
     * 判断是否应该使用远程调度
     */
    public boolean shouldUseRemote() {
        return mode == DispatchMode.REMOTE || mode == DispatchMode.AUTO;
    }

    /**
     * 判断是否应该回退本地调度
     */
    public boolean shouldFallbackToLocal() {
        return mode == DispatchMode.AUTO || mode == DispatchMode.LOCAL;
    }

    @Override
    public String toString() {
        return "A2AConfig{mode=" + mode + ", registry=" + registryEndpoint +
               ", basePort=" + basePort + "}";
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private DispatchMode mode = DispatchMode.LOCAL;
        private String registryEndpoint = "http://localhost:9100";
        private int basePort = 9100;
        private int connectTimeoutSeconds = 10;
        private int requestTimeoutSeconds = 30;
        private boolean autoRefreshCards = true;
        private int refreshIntervalSeconds = 60;

        public Builder mode(DispatchMode mode) { this.mode = mode; return this; }
        public Builder registryEndpoint(String endpoint) { this.registryEndpoint = endpoint; return this; }
        public Builder basePort(int basePort) { this.basePort = basePort; return this; }
        public Builder connectTimeoutSeconds(int sec) { this.connectTimeoutSeconds = sec; return this; }
        public Builder requestTimeoutSeconds(int sec) { this.requestTimeoutSeconds = sec; return this; }
        public Builder autoRefreshCards(boolean auto) { this.autoRefreshCards = auto; return this; }
        public Builder refreshIntervalSeconds(int sec) { this.refreshIntervalSeconds = sec; return this; }

        public A2AConfig build() {
            A2AConfig config = new A2AConfig();
            config.mode = this.mode;
            config.registryEndpoint = this.registryEndpoint;
            config.basePort = this.basePort;
            config.connectTimeoutSeconds = this.connectTimeoutSeconds;
            config.requestTimeoutSeconds = this.requestTimeoutSeconds;
            config.autoRefreshCards = this.autoRefreshCards;
            config.refreshIntervalSeconds = this.refreshIntervalSeconds;
            return config;
        }
    }
}
