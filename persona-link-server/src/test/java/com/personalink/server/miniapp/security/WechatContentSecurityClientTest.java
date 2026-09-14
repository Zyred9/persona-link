package com.personalink.server.miniapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.miniapp.WechatAccessTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));

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
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));

        assertTrue(this.client.isTextAllowed("openid-a", "内容", WechatContentSecurityClient.SCENE_COMMENT));
        this.server.verify();
    }

    @Test
    void allowsWhenUnconfigured() {
        this.configureClient("", "");

        assertTrue(this.client.isTextAllowed("openid-a", "内容", WechatContentSecurityClient.SCENE_COMMENT));
        this.server.verify();
    }

    @Test
    void allowsWhenWechatCallFails() {
        this.configureClient("app-id", "app-secret");
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo(CHECK_URL)).andRespond(withServerError());

        assertTrue(this.client.isTextAllowed("openid-a", "内容", WechatContentSecurityClient.SCENE_COMMENT));
        this.server.verify();
    }

    @Test
    void allowsOnOtherWechatErrors() {
        this.configureClient("app-id", "app-secret");
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-1\"}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo(CHECK_URL))
                .andRespond(withSuccess("{\"errcode\":45009,\"errmsg\":\"reach max api daily quota limit\"}",
                        MediaType.APPLICATION_JSON));

        assertTrue(this.client.isTextAllowed("openid-a", "内容", WechatContentSecurityClient.SCENE_COMMENT));
        this.server.verify();
    }

    @Test
    void skipsBlankTextWithoutNetworkCall() {
        this.configureClient("app-id", "app-secret");

        assertTrue(this.client.isTextAllowed("openid-a", "   ", WechatContentSecurityClient.SCENE_PROFILE));
        this.server.verify();
    }
}
