package com.personalink.server.miniapp.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.miniapp.WechatAccessTokenProvider;
import com.personalink.server.miniapp.WechatRestClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 微信内容安全校验（文本 msgSecCheck v2、图片 mediaCheckAsync v2）；仅明确通过时放行，审核异常提示重试。 */
@Component
public class WechatContentSecurityClient {

    /** 场景：资料（昵称、头像等）。 */
    public static final int SCENE_PROFILE = 1;
    /** 场景：评论（反馈等）。 */
    public static final int SCENE_COMMENT = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(WechatContentSecurityClient.class);
    private static final String WECHAT_API_BASE_URL = "https://api.weixin.qq.com";
    /** 微信判定文本含违规内容的错误码。 */
    private static final int RISKY_CONTENT_ERROR_CODE = 87014;
    /** 微信异步审核的媒体类型：图片。 */
    private static final int MEDIA_TYPE_IMAGE = 2;
    /** 凭证失效错误码，命中后刷新凭证重试一次。 */
    private static final Set<Integer> INVALID_TOKEN_ERROR_CODES = Set.of(40001, 42001);

    private final WechatAccessTokenProvider accessTokenProvider;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public WechatContentSecurityClient(WechatAccessTokenProvider accessTokenProvider, ObjectMapper objectMapper) {
        this(accessTokenProvider, objectMapper,
                WechatRestClientFactory.builder().baseUrl(WECHAT_API_BASE_URL).build());
    }

    WechatContentSecurityClient(WechatAccessTokenProvider accessTokenProvider, ObjectMapper objectMapper,
                                RestClient restClient) {
        this.accessTokenProvider = accessTokenProvider;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    /**
     * 校验文本内容是否合规。
     * @param openId 提交内容的用户
     * @param content 待校验文本，为空直接通过
     * @param scene 微信内容安全场景值，见 SCENE_ 常量
     * @return 明确通过返回 true；违规或待复核返回 false
     * @throws BusinessException 凭证缺失、接口异常或审核结果不完整时提示重试
     */
    public boolean isTextAllowed(String openId, String content, int scene) {
        if (!StringUtils.hasText(content)) {
            return true;
        }
        JsonNode root = this.callWithRetry("文本校验", openId, scene,
                accessToken -> this.checkText(openId, content, scene, accessToken));
        int errorCode = root.path("errcode").asInt(-1);
        if (RISKY_CONTENT_ERROR_CODE == errorCode) {
            return false;
        }
        if (errorCode != 0) {
            LOGGER.warn("[内容安全] 微信接口返回错误，用户：{}，场景：{}，错误码：{}，响应：{}", openId, scene, errorCode, root);
            throw this.checkUnavailable();
        }
        // v2 的 errcode 仅表示调用是否成功，内容结论必须读取 result.suggest。
        String suggestion = root.path("result").path("suggest").asText();
        if ("pass".equals(suggestion)) {
            return true;
        }
        if ("risky".equals(suggestion) || "review".equals(suggestion)) {
            return false;
        }
        LOGGER.warn("[内容安全] 微信审核结论缺失或未知，用户：{}，场景：{}，响应：{}", openId, scene, root);
        throw this.checkUnavailable();
    }

    /**
     * 提交图片异步内容安全审核。
     * @param openId 提交图片的用户
     * @param mediaUrl 公网可访问的图片地址，微信服务器会主动拉取
     * @param scene 微信内容安全场景值，见 SCENE_ 常量
     * @return 微信审核任务号 trace_id，审核结论经消息推送异步返回
     * @throws BusinessException 凭证缺失、接口异常或未返回任务号时提示重试
     */
    public String checkImageAsync(String openId, String mediaUrl, int scene) {
        Assert.hasText(mediaUrl, "图片地址不能为空");
        JsonNode root = this.callWithRetry("图片审核提交", openId, scene,
                accessToken -> this.checkMediaAsync(openId, mediaUrl, scene, accessToken));
        int errorCode = root.path("errcode").asInt(-1);
        if (errorCode != 0) {
            LOGGER.warn("[内容安全] 微信图片审核提交失败，用户：{}，场景：{}，错误码：{}，响应：{}", openId, scene, errorCode, root);
            throw this.checkUnavailable();
        }
        String traceId = root.path("trace_id").asText();
        if (!StringUtils.hasText(traceId)) {
            LOGGER.warn("[内容安全] 微信图片审核未返回任务号，用户：{}，场景：{}，响应：{}", openId, scene, root);
            throw this.checkUnavailable();
        }
        return traceId;
    }

    private BusinessException checkUnavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50351, "内容审核服务暂时不可用，请稍后重试");
    }

    /** 携带有效凭证调用微信接口；凭证失效时刷新一次重试，其余异常统一提示重试。 */
    private JsonNode callWithRetry(String action, String openId, int scene, Function<String, JsonNode> request) {
        if (!this.accessTokenProvider.isConfigured()) {
            LOGGER.warn("[内容安全] 微信配置缺失，无法{}，用户：{}，场景：{}", action, openId, scene);
            throw this.checkUnavailable();
        }
        try {
            JsonNode root = request.apply(this.accessTokenProvider.getToken());
            if (INVALID_TOKEN_ERROR_CODES.contains(root.path("errcode").asInt(-1))) {
                this.accessTokenProvider.invalidate();
                root = request.apply(this.accessTokenProvider.getToken());
            }
            return root;
        } catch (RestClientResponseException exception) {
            LOGGER.error("[内容安全] {}调用失败，用户：{}，场景：{}，HTTP状态：{}，响应：{}",
                    action, openId, scene, exception.getStatusCode().value(),
                    exception.getResponseBodyAsString(), exception);
            throw this.checkUnavailable();
        } catch (RestClientException | BusinessException exception) {
            LOGGER.error("[内容安全] {}调用失败，用户：{}，场景：{}", action, openId, scene, exception);
            throw this.checkUnavailable();
        }
    }

    private JsonNode checkText(String openId, String content, int scene, String accessToken) {
        String responseBody = this.restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/wxa/msg_sec_check")
                        .queryParam("access_token", accessToken)
                        .build())
                .body(Map.of("content", content, "version", 2, "scene", scene, "openid", openId))
                .retrieve()
                .body(String.class);
        return this.parseJson(responseBody);
    }

    private JsonNode checkMediaAsync(String openId, String mediaUrl, int scene, String accessToken) {
        String responseBody = this.restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/wxa/media_check_async")
                        .queryParam("access_token", accessToken)
                        .build())
                .body(Map.of("media_url", mediaUrl, "media_type", MEDIA_TYPE_IMAGE, "version", 2,
                        "scene", scene, "openid", openId))
                .retrieve()
                .body(String.class);
        return this.parseJson(responseBody);
    }

    private JsonNode parseJson(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            LOGGER.warn("[内容安全] 微信响应为空，无法解析");
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50251, "微信服务返回异常，请稍后重试");
        }
        try {
            return this.objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("[内容安全] 响应解析失败，响应：{}", responseBody, exception);
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50251, "微信服务返回异常，请稍后重试");
        }
    }
}
