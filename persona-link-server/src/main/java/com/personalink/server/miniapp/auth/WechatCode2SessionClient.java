package com.personalink.server.miniapp.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.WechatCode2SessionResponse;
import com.personalink.server.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Objects;

/**
 * 微信 code2Session 调用客户端。
 */
@Component
public class WechatCode2SessionClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(WechatCode2SessionClient.class);
    private static final String WECHAT_API_BASE_URL = "https://api.weixin.qq.com";

    private final String appId;
    private final String appSecret;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public WechatCode2SessionClient(@Value("${WECHAT_APP_ID:}") String appId,
                                    @Value("${WECHAT_APP_SECRET:}") String appSecret,
                                    ObjectMapper objectMapper) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(WECHAT_API_BASE_URL)
                .build();
    }

    public String exchange(String code) {
        if (!StringUtils.hasText(this.appId) || !StringUtils.hasText(this.appSecret)) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50301, "微信登录配置未完成");
        }

        String responseBody;
        try {
            responseBody = this.restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sns/jscode2session")
                            .queryParam("appid", this.appId)
                            .queryParam("secret", this.appSecret)
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException exception) {
            LOGGER.warn("[微信登录] code2Session 调用失败，异常类型：{}", exception.getClass().getSimpleName());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50201, "微信服务暂不可用，请稍后重试");
        }

        WechatCode2SessionResponse response = this.parseResponse(responseBody);
        if (Objects.isNull(response)) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50201, "微信服务暂不可用，请稍后重试");
        }
        if (Objects.nonNull(response.errorCode()) && response.errorCode() != 0) {
            LOGGER.warn("[微信登录] code2Session 返回错误，错误码：{}", response.errorCode());
            throw this.wechatError(response.errorCode());
        }
        if (!StringUtils.hasText(response.openId())) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50201, "微信服务返回异常，请稍后重试");
        }
        return response.openId();
    }

    WechatCode2SessionResponse parseResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50201, "微信服务返回异常，请稍后重试");
        }
        try {
            return this.objectMapper.readValue(responseBody, WechatCode2SessionResponse.class);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("[微信登录] code2Session 响应解析失败，异常类型：{}", exception.getClass().getSimpleName());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50201, "微信服务返回异常，请稍后重试");
        }
    }

    private BusinessException wechatError(int errorCode) {
        return switch (errorCode) {
            case 40029, 40163 -> new BusinessException(
                    HttpStatus.UNAUTHORIZED, 40103, "微信登录凭证无效，请重新授权");
            case 45011 -> new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS, 42901, "微信登录请求过于频繁，请稍后重试");
            case 40013, 40125, 41002, 41004 -> new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE, 50302, "微信应用配置错误");
            default -> new BusinessException(
                    HttpStatus.BAD_GATEWAY, 50202, "微信登录失败，请稍后重试");
        };
    }
}
