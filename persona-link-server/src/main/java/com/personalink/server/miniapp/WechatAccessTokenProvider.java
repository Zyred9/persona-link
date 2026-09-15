package com.personalink.server.miniapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;

/** 微信小程序 access_token 统一缓存；多个客户端共用一份凭证，避免重复获取。 */
@Component
public class WechatAccessTokenProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(WechatAccessTokenProvider.class);
    private static final String WECHAT_API_BASE_URL = "https://api.weixin.qq.com";
    /** 提前刷新时长，避免临界过期。 */
    private static final long REFRESH_AHEAD_SECONDS = 300L;

    private final String appId;
    private final String appSecret;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private volatile String accessToken;
    private volatile Instant accessTokenExpiresAt = Instant.EPOCH;

    @Autowired
    public WechatAccessTokenProvider(@Value("${wechat.app-id:}") String appId,
                                     @Value("${wechat.app-secret:}") String appSecret,
                                     ObjectMapper objectMapper) {
        this(appId, appSecret, objectMapper,
                WechatRestClientFactory.builder().baseUrl(WECHAT_API_BASE_URL).build());
    }

    public WechatAccessTokenProvider(String appId, String appSecret, ObjectMapper objectMapper, RestClient restClient) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    /** AppID 与 AppSecret 均已配置时可用。 */
    public boolean isConfigured() {
        return StringUtils.hasText(this.appId) && StringUtils.hasText(this.appSecret);
    }

    /** 返回缓存中的有效凭证，缺失或临近过期时重新获取。 */
    public String getToken() {
        if (!this.isConfigured()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50341,
                    "微信小程序 AppID 或 AppSecret 未配置");
        }
        String cached = this.accessToken;
        if (StringUtils.hasText(cached) && Instant.now().isBefore(this.accessTokenExpiresAt)) {
            return cached;
        }
        return this.refresh();
    }

    /** 微信判定凭证失效时清空缓存，下次调用重新获取。 */
    public synchronized void invalidate() {
        this.accessToken = null;
        this.accessTokenExpiresAt = Instant.EPOCH;
    }

    private synchronized String refresh() {
        String cached = this.accessToken;
        if (StringUtils.hasText(cached) && Instant.now().isBefore(this.accessTokenExpiresAt)) {
            return cached;
        }
        try {
            String responseBody = this.restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/cgi-bin/token")
                            .queryParam("grant_type", "client_credential")
                            .queryParam("appid", this.appId)
                            .queryParam("secret", this.appSecret)
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode root = this.parseJson(responseBody);
            int errorCode = root.path("errcode").asInt(0);
            if (errorCode != 0) {
                LOGGER.warn("[微信凭证] access_token 获取失败，错误码：{}，响应：{}", errorCode, root);
                throw new BusinessException(HttpStatus.BAD_GATEWAY, 50241, "微信服务暂不可用，请稍后重试");
            }
            String token = root.path("access_token").asText();
            if (!StringUtils.hasText(token)) {
                LOGGER.warn("[微信凭证] access_token 缺失，响应：{}", root);
                throw new BusinessException(HttpStatus.BAD_GATEWAY, 50241, "微信服务返回异常，请稍后重试");
            }
            int expiresIn = root.path("expires_in").asInt(7200);
            this.accessToken = token;
            this.accessTokenExpiresAt = Instant.now()
                    .plusSeconds(Math.max(60L, expiresIn - REFRESH_AHEAD_SECONDS));
            return token;
        } catch (RestClientResponseException exception) {
            LOGGER.error("[微信凭证] access_token 调用失败，HTTP状态：{}，响应：{}",
                    exception.getStatusCode().value(), exception.getResponseBodyAsString(), exception);
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50241, "微信服务暂不可用，请稍后重试");
        } catch (RestClientException exception) {
            LOGGER.error("[微信凭证] access_token 调用失败", exception);
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50241, "微信服务暂不可用，请稍后重试");
        }
    }

    private JsonNode parseJson(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            LOGGER.warn("[微信凭证] access_token 响应为空");
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50241, "微信服务返回异常，请稍后重试");
        }
        try {
            return this.objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("[微信凭证] access_token 响应解析失败，响应：{}", responseBody, exception);
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50241, "微信服务返回异常，请稍后重试");
        }
    }
}
