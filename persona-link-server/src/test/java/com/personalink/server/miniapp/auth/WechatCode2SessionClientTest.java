package com.personalink.server.miniapp.auth;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.WechatCode2SessionResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WechatCode2SessionClientTest {

    @Test
    void shouldParseWechatResponseFromRawJson() {
        ObjectMapper objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        WechatCode2SessionClient client = new WechatCode2SessionClient(
                "app-id", "app-secret", objectMapper);

        WechatCode2SessionResponse response = client.parseResponse(
                "{\"openid\":\"openid-1\",\"session_key\":\"session-key-1\"}");

        assertEquals("openid-1", response.openId());
    }
}
