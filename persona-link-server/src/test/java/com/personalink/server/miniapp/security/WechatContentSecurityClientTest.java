package com.personalink.server.miniapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.miniapp.WechatAccessTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WechatContentSecurityClientTest {

    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token"
            + "?grant_type=client_credential&appid=app-id&secret=app-secret";
    private static final String CHECK_URL = "https://api.weixin.qq.com/wxa/msg_sec_check?access_token=token-1";
    private static final String MEDIA_CHECK_URL = "https://api.weixin.qq.com/wxa/media_check_async?access_token=token-1";

    private MockRestServiceServer server;
    private RestClient restClient;
    private WechatContentSecurityClient client;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.weixin.qq.com");
        this.server = MockRestServiceServer.bindTo(builder).build();
        this.restClient = builder.build();
    }

    private void configureClient(String appId, String appSecret) {
        WechatAccessTokenProvider tokenProvider = new WechatAccessTokenProvider(
                appId, appSecret, new ObjectMapper(), this.restClient);
        this.client = new WechatContentSecurityClient(tokenProvider, new ObjectMapper(), this.restClient);
    }

    @Test
    void allowsCleanTextAndSendsVersionTwoPayload() {
        this.configureClient("app-id", "app-secret");
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo(CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.content").value("反馈内容"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.scene").value(2))
                .andExpect(jsonPath("$.openid").value("openid-a"))
                .andRespond(withSuccess("{\"errcode\":0,\"result\":{\"suggest\":\"pass\"}}", MediaType.APPLICATION_JSON));

        assertTrue(this.client.isTextAllowed("openid-a", "反馈内容", WechatContentSecurityClient.SCENE_COMMENT));
        this.server.verify();
    }

    @Test
    void rejectsRiskyText() {
        this.configureClient("app-id", "app-secret");
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo(CHECK_URL))
                .andRespond(withSuccess("{\"errcode\":87014,\"errmsg\":\"risky content\"}", MediaType.APPLICATION_JSON));

        assertFalse(this.client.isTextAllowed("openid-a", "违规文本", WechatContentSecurityClient.SCENE_PROFILE));
        this.server.verify();
    }

    @Test
    void refreshesTokenOnceAfterInvalidCredential() {
        this.configureClient("app-id", "app-secret");
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo(CHECK_URL))
                .andRespond(withSuccess("{\"errcode\":42001,\"errmsg\":\"access_token expired\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-2\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo("https://api.weixin.qq.com/wxa/msg_sec_check?access_token=token-2"))
                .andRespond(withSuccess("{\"errcode\":0,\"result\":{\"suggest\":\"pass\"}}", MediaType.APPLICATION_JSON));

        assertTrue(this.client.isTextAllowed("openid-a", "内容", WechatContentSecurityClient.SCENE_COMMENT));
        this.server.verify();
    }

    @Test
    void rejectsWhenUnconfiguredWithRetryMessage() {
        this.configureClient("", "");

        this.assertCheckUnavailable();
        this.server.verify();
    }

    @Test
    void rejectsWhenWechatCallFailsWithRetryMessage() {
        this.configureClient("app-id", "app-secret");
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo(CHECK_URL)).andRespond(withServerError());

        this.assertCheckUnavailable();
        this.server.verify();
    }

    @Test
    void rejectsOnOtherWechatErrorsWithRetryMessage() {
        this.configureClient("app-id", "app-secret");
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo(CHECK_URL))
                .andRespond(withSuccess("{\"errcode\":45009,\"errmsg\":\"reach max api daily quota limit\"}",
                        MediaType.APPLICATION_JSON));

        this.assertCheckUnavailable();
        this.server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"risky", "review"})
    void rejectsVersionTwoRiskDecisions(String suggestion) {
        this.configureClient("app-id", "app-secret");
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo(CHECK_URL))
                .andRespond(withSuccess("{\"errcode\":0,\"result\":{\"suggest\":\"" + suggestion + "\"}}",
                        MediaType.APPLICATION_JSON));

        assertFalse(this.client.isTextAllowed("openid-a", "待审核文本", WechatContentSecurityClient.SCENE_PROFILE));
        this.server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid-json", "null", "{}", "{\"errcode\":0}",
            "{\"result\":{\"suggest\":\"pass\"}}", "{\"errcode\":0,\"result\":{\"suggest\":\"unknown\"}}"})
    void rejectsMalformedOrIncompleteDecisionsWithRetryMessage(String response) {
        this.configureClient("app-id", "app-secret");
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo(CHECK_URL)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        this.assertCheckUnavailable();
        this.server.verify();
    }

    @Test
    void rejectsWhenTokenFetchFailsWithRetryMessage() {
        this.configureClient("app-id", "app-secret");
        this.server.expect(requestTo(TOKEN_URL)).andRespond(withServerError());

        this.assertCheckUnavailable();
        this.server.verify();
    }

    @Test
    void submitsImageCheckWithVersionTwoPayloadAndReturnsTraceId() {
        this.configureClient("app-id", "app-secret");
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo(MEDIA_CHECK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.media_url").value("https://cdn.example/avatar.png"))
                .andExpect(jsonPath("$.media_type").value(2))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.scene").value(1))
                .andExpect(jsonPath("$.openid").value("openid-a"))
                .andRespond(withSuccess("{\"errcode\":0,\"trace_id\":\"trace-1\"}", MediaType.APPLICATION_JSON));

        assertEquals("trace-1", this.client.checkImageAsync("openid-a", "https://cdn.example/avatar.png",
                WechatContentSecurityClient.SCENE_PROFILE));
        this.server.verify();
    }

    @Test
    void rejectsImageCheckWithoutTraceIdWithRetryMessage() {
        this.configureClient("app-id", "app-secret");
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo(MEDIA_CHECK_URL))
                .andRespond(withSuccess("{\"errcode\":0}", MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> this.client.checkImageAsync("openid-a", "https://cdn.example/avatar.png",
                        WechatContentSecurityClient.SCENE_PROFILE));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getHttpStatus());
        assertEquals(50351, exception.getCode());
        this.server.verify();
    }

    @Test
    void refreshesTokenOnceForImageCheck() {
        this.configureClient("app-id", "app-secret");
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo(MEDIA_CHECK_URL))
                .andRespond(withSuccess("{\"errcode\":42001,\"errmsg\":\"access_token expired\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-2\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo("https://api.weixin.qq.com/wxa/media_check_async?access_token=token-2"))
                .andRespond(withSuccess("{\"errcode\":0,\"trace_id\":\"trace-2\"}", MediaType.APPLICATION_JSON));

        assertEquals("trace-2", this.client.checkImageAsync("openid-a", "https://cdn.example/avatar.png",
                WechatContentSecurityClient.SCENE_PROFILE));
        this.server.verify();
    }

    private void assertCheckUnavailable() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> this.client.isTextAllowed("openid-a", "内容", WechatContentSecurityClient.SCENE_COMMENT));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getHttpStatus());
        assertEquals(50351, exception.getCode());
        assertEquals("内容审核服务暂时不可用，请稍后重试", exception.getMessage());
    }

    @Test
    void skipsBlankTextWithoutNetworkCall() {
        this.configureClient("app-id", "app-secret");

        assertTrue(this.client.isTextAllowed("openid-a", "   ", WechatContentSecurityClient.SCENE_PROFILE));
        this.server.verify();
    }
}
