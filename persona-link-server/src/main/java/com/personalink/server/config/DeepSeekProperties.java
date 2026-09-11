package com.personalink.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** DeepSeek API 配置，密钥只从运行环境注入。 */
@Component
@ConfigurationProperties(prefix = "app.deepseek")
public class DeepSeekProperties {

    private String baseUrl = "https://api.deepseek.com";
    private String apiKey;
    private String model = "deepseek-flash";
    private int connectTimeoutMillis = 10000;
    private int readTimeoutMillis = 120000;

    public String getBaseUrl() { return this.baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return this.apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return this.model; }
    public void setModel(String model) { this.model = model; }
    public int getConnectTimeoutMillis() { return this.connectTimeoutMillis; }
    public void setConnectTimeoutMillis(int connectTimeoutMillis) { this.connectTimeoutMillis = connectTimeoutMillis; }
    public int getReadTimeoutMillis() { return this.readTimeoutMillis; }
    public void setReadTimeoutMillis(int readTimeoutMillis) { this.readTimeoutMillis = readTimeoutMillis; }
}
