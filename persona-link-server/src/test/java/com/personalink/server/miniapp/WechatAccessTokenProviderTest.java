package com.personalink.server.miniapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WechatAccessTokenProviderTest {

    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token"
            + "?grant_type=client_credential&appid=app-id&secret=app-secret";

    private MockRestServiceServer server;
    private RestClient restClient;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.weixin.qq.com");
        this.server = MockRestServiceServer.bindTo(builder).build();
        this.restClient = builder.build();
    }

    @Test
    void cachesTokenAcrossCalls() {
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-1\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        WechatAccessTokenProvider provider = new WechatAccessTokenProvider(
                "app-id", "app-secret", new ObjectMapper(), this.restClient);

        assertEquals("token-1", provider.getToken());
        assertEquals("token-1", provider.getToken());
        this.server.verify();
    }

    @Test
    void invalidateForcesRefresh() {
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-1\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"token-2\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        WechatAccessTokenProvider provider = new WechatAccessTokenProvider(
                "app-id", "app-secret", new ObjectMapper(), this.restClient);

        assertEquals("token-1", provider.getToken());
        provider.invalidate();
        assertEquals("token-2", provider.getToken());
        this.server.verify();
    }

    @Test
    void rejectsMissingConfigurationWithoutNetworkCall() {
        WechatAccessTokenProvider provider = new WechatAccessTokenProvider(
                "", "", new ObjectMapper(), this.restClient);

        assertThrows(BusinessException.class, provider::getToken);
        this.server.verify();
    }

    @Test
    void rejectsWechatErrorResponse() {
        this.server.expect(anything())
                .andRespond(withSuccess("{\"errcode\":40013,\"errmsg\":\"invalid appid\"}", MediaType.APPLICATION_JSON));
        WechatAccessTokenProvider provider = new WechatAccessTokenProvider(
                "app-id", "app-secret", new ObjectMapper(), this.restClient);

        assertThrows(BusinessException.class, provider::getToken);
        this.server.verify();
    }
}
