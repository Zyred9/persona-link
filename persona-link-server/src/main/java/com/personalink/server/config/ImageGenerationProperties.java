package com.personalink.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 生图路由配置；1 为阿里百炼，2 为火山即梦。密钥只从后端环境注入。 */
@Component
@ConfigurationProperties(prefix = "app.image-generation")
public class ImageGenerationProperties {
    private int provider = 1;
    private String qwenBaseUrl = "https://dashscope.aliyuncs.com";
    private String qwenApiKey;
    private String qwenModel = "qwen-image-3.0";
    private String volcBaseUrl = "https://visual.volcengineapi.com";
    private String volcAccessKey;
    private String volcSecretKey;
    private String volcModel = "jimeng_t2i_v40";
    private String volcRegion = "cn-north-1";
    private int connectTimeoutMillis = 10000;
    private int readTimeoutMillis = 30000;
    private int pollIntervalMillis = 5000;
    private int taskTimeoutSeconds = 1200;

    public int getProvider() { return this.provider; }
    public void setProvider(int value) { this.provider = value; }
    public String getQwenBaseUrl() { return this.qwenBaseUrl; }
    public void setQwenBaseUrl(String value) { this.qwenBaseUrl = value; }
    public String getQwenApiKey() { return this.qwenApiKey; }
    public void setQwenApiKey(String value) { this.qwenApiKey = value; }
    public String getQwenModel() { return this.qwenModel; }
    public void setQwenModel(String value) { this.qwenModel = value; }
    public String getVolcBaseUrl() { return this.volcBaseUrl; }
    public void setVolcBaseUrl(String value) { this.volcBaseUrl = value; }
    public String getVolcAccessKey() { return this.volcAccessKey; }
    public void setVolcAccessKey(String value) { this.volcAccessKey = value; }
    public String getVolcSecretKey() { return this.volcSecretKey; }
    public void setVolcSecretKey(String value) { this.volcSecretKey = value; }
    public String getVolcModel() { return this.volcModel; }
    public void setVolcModel(String value) { this.volcModel = value; }
    public String getVolcRegion() { return this.volcRegion; }
    public void setVolcRegion(String value) { this.volcRegion = value; }
    public int getConnectTimeoutMillis() { return this.connectTimeoutMillis; }
    public void setConnectTimeoutMillis(int value) { this.connectTimeoutMillis = value; }
    public int getReadTimeoutMillis() { return this.readTimeoutMillis; }
    public void setReadTimeoutMillis(int value) { this.readTimeoutMillis = value; }
    public int getPollIntervalMillis() { return this.pollIntervalMillis; }
    public void setPollIntervalMillis(int value) { this.pollIntervalMillis = value; }
    public int getTaskTimeoutSeconds() { return this.taskTimeoutSeconds; }
    public void setTaskTimeoutSeconds(int value) { this.taskTimeoutSeconds = value; }
}
