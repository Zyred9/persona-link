package com.personalink.server.miniapp.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.miniapp.WechatAccessTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Set;

/** 微信文本内容安全校验（msgSecCheck v2）；命中违规判定为不通过，接口异常记录日志后放行。 */
@Component
public class WechatContentSecurityClient {

    /** 场景：资料（昵称等）。 */
    public static final int SCENE_PROFILE = 1;
    /** 场景：评论（反馈等）。 */
    public static final int SCENE_COMMENT = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(WechatContentSecurityClient.class);
    private static final String WECHAT_API_BASE_URL = "https://api.weixin.qq.com";
    /** 微信判定文本含违规内容的错误码。 */
    private static final int RISKY_CONTENT_ERROR_CODE = 87014;
    /** 凭证失效错误码，命中后刷新凭证重试一次。 */
    private static final Set<Integer> INVALID_TOKEN_ERROR_CODES = Set.of(40001, 42001);

    private final WechatAccessTokenProvider accessTokenProvider;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public WechatContentSecurityClient(WechatAccessTokenProvider accessTokenProvider, ObjectMapper objectMapper) {
        this(accessTokenProvider, objectMapper,
                RestClient.builder().baseUrl(WECHAT_API_BASE_URL).build());
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
     * @return 通过返回 true；命中违规返回 false；凭证缺失或接口异常放行返回 true
     */
    public boolean isTextAllowed(String openId, String content, int scene) {
        if (!StringUtils.hasText(content)) {
            return true;
        }
        if (!this.accessTokenProvider.isConfigured()) {
            LOGGER.warn("[内容安全] 微信配置缺失，跳过文本校验，用户：{}，场景：{}", openId, scene);
            return true;
        }
        try {
            JsonNode root = this.checkText(openId, content, scene, this.accessTokenProvider.getToken());
            int errorCode = root.path("errcode").asInt(0);
            if (INVALID_TOKEN_ERROR_CODES.contains(errorCode)) {
                this.accessTokenProvider.invalidate();
                root = this.checkText(openId, content, scene, this.accessTokenProvider.getToken());
                errorCode = root.path("errcode").asInt(0);
            }
            if (RISKY_CONTENT_ERROR_CODE == errorCode) {
                LOGGER.warn("[内容安全] 文本命中违规内容，用户：{}，场景：{}", openId, scene);
                return false;
            }
            if (errorCode != 0) {
                LOGGER.warn("[内容安全] 微信接口返回错误，用户：{}，场景：{}，错误码：{}", openId, scene, errorCode);
            }
            return true;
        } catch (RestClientException | BusinessException exception) {
            LOGGER.error("[内容安全] 文本校验调用失败，用户：{}，场景：{}，按放行处理", openId, scene, exception);
            return true;
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

    private JsonNode parseJson(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50251, "微信服务返回异常，请稍后重试");
        }
        try {
            return this.objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("[内容安全] 响应解析失败，异常类型：{}", exception.getClass().getSimpleName());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50251, "微信服务返回异常，请稍后重试");
        }
    }
}
