package com.personalink.server.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.config.ImageGenerationProperties;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.util.VolcImageSignUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ImageProviderClientTest {
    private ImageGenerationProperties properties;
    private MockRestServiceServer server;
    private ImageProviderClient client;

    @BeforeEach
    void setup() {
        this.properties = new ImageGenerationProperties();
        this.properties.setQwenApiKey("test-key");
        this.properties.setVolcAccessKey("test-ak");
        this.properties.setVolcSecretKey("test-sk");
        RestClient.Builder builder = RestClient.builder();
        this.server = MockRestServiceServer.bindTo(builder).build();
        this.client = new ImageProviderClient(this.properties, new ObjectMapper(), builder.build());
    }

    @Test
    void qwenNativeTaskUsesNewProtocolAndPersistedRoute() {
        ImageProviderRoute route = this.client.currentRoute();
        this.server.expect(requestTo("https://dashscope.aliyuncs.com/api/v1/services/aigc/image-generation/generation"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("X-DashScope-Async", "enable"))
                .andExpect(jsonPath("$.input.messages[0].content[0].text").value("详情图1100×500"))
                .andExpect(jsonPath("$.model").value("qwen-image-3.0"))
                .andExpect(jsonPath("$.parameters.size").value("1100*500"))
                .andExpect(jsonPath("$.parameters.n").value(1))
                .andRespond(withSuccess("{\"output\":{\"task_id\":\"job-1\",\"task_status\":\"PENDING\"}}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo("https://dashscope.aliyuncs.com/api/v1/tasks/job-1"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess("{\"output\":{\"task_status\":\"SUCCEEDED\",\"choices\":[{\"message\":{\"content\":[{\"image\":\"https://result.aliyuncs.com/image.png\"}]}}]}}", MediaType.APPLICATION_JSON));
        assertEquals("job-1", this.client.submit(route, "详情图1100×500", 1100, 500));
        this.properties.setProvider(2);
        assertEquals(2, this.client.query(route, "job-1").state());
        this.server.verify();
    }

    @Test
    void volcNativeSubmitAndQueryAreSignedAndSingleImage() {
        this.properties.setProvider(2);
        ImageProviderRoute route = this.client.currentRoute();
        this.server.expect(requestTo("https://visual.volcengineapi.com/?Action=CVSync2AsyncSubmitTask&Version=2022-08-31"))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("HMAC-SHA256 Credential=test-ak/")))
                .andExpect(jsonPath("$.req_key").value("jimeng_t2i_v40"))
                .andExpect(jsonPath("$.force_single").value(true))
                .andExpect(jsonPath("$.width").value(1600))
                .andRespond(withSuccess("{\"code\":10000,\"data\":{\"task_id\":\"123\"}}", MediaType.APPLICATION_JSON));
        this.server.expect(requestTo("https://visual.volcengineapi.com/?Action=CVSync2AsyncGetResult&Version=2022-08-31"))
                .andExpect(jsonPath("$.task_id").value("123"))
                .andRespond(withSuccess("{\"code\":10000,\"data\":{\"status\":\"done\",\"image_urls\":[\"https://image.volces.com/image.png\"]}}", MediaType.APPLICATION_JSON));
        assertEquals("123", this.client.submit(route, "封面图800×800", 800, 800));
        assertEquals("https://image.volces.com/image.png", this.client.query(route, "123").imageUrl());
        this.server.verify();
    }

    @Test
    void customPortraitResolutionReachesBothProviders() {
        this.server.expect(anything()).andExpect(jsonPath("$.parameters.size").value("600*900"))
                .andRespond(withSuccess("{\"output\":{\"task_id\":\"q1\"}}", MediaType.APPLICATION_JSON));
        this.server.expect(anything()).andExpect(jsonPath("$.width").value(1200))
                .andExpect(jsonPath("$.height").value(1800))
                .andRespond(withSuccess("{\"code\":10000,\"data\":{\"task_id\":\"v1\"}}", MediaType.APPLICATION_JSON));
        assertEquals("q1", this.client.submit(this.client.currentRoute(), "详情", 600, 900));
        this.properties.setProvider(2);
        assertEquals("v1", this.client.submit(this.client.currentRoute(), "详情", 600, 900));
        this.server.verify();
    }

    @Test
    void wideResolutionIsSubmittedUnchangedToBothProviders() {
        this.server.expect(anything()).andExpect(jsonPath("$.parameters.size").value("2200*1000"))
                .andRespond(withSuccess("{\"output\":{\"task_id\":\"q1\"}}", MediaType.APPLICATION_JSON));
        this.server.expect(anything()).andExpect(jsonPath("$.width").value(2200))
                .andExpect(jsonPath("$.height").value(1000))
                .andRespond(withSuccess("{\"code\":10000,\"data\":{\"task_id\":\"v1\"}}", MediaType.APPLICATION_JSON));
        assertEquals("q1", this.client.submit(this.client.currentRoute(), "详情", 2200, 1000));
        this.properties.setProvider(2);
        assertEquals("v1", this.client.submit(this.client.currentRoute(), "详情", 2200, 1000));
        this.server.verify();
    }

    @Test
    void unknownSubmissionIsNotSafeToRetry() {
        this.server.expect(anything()).andRespond(withServerError());
        assertThrows(ImageSubmissionUncertainException.class,
                () -> this.client.submit(this.client.currentRoute(), "封面", 800, 800));
        this.server.verify();
    }

    @Test
    void missingTaskIdIsUncertainAndNotFabricated() {
        this.server.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThrows(ImageSubmissionUncertainException.class,
                () -> this.client.submit(this.client.currentRoute(), "封面", 800, 800));
    }

    @Test
    void malformedVolcResponseIsUncertainButExplicitRejectionIsNot() {
        this.properties.setProvider(2);
        this.server.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        this.server.expect(anything()).andRespond(withBadRequest());
        assertThrows(ImageSubmissionUncertainException.class,
                () -> this.client.submit(this.client.currentRoute(), "封面", 800, 800));
        assertThrows(ImageSubmissionRejectedException.class,
                () -> this.client.submit(this.client.currentRoute(), "封面", 800, 800));
        this.server.verify();
    }

    @Test
    void runtimeFailureAfterSendingMustRemainUncertain() {
        this.server.expect(anything()).andRespond(request -> {
            throw new IllegalStateException("unexpected response parser failure");
        });
        assertThrows(ImageSubmissionUncertainException.class,
                () -> this.client.submit(this.client.currentRoute(), "封面", 800, 800));
        this.server.verify();
    }

    @Test
    void preflightValidationIsExplicitlyRejected() {
        assertThrows(ImageSubmissionRejectedException.class,
                () -> this.client.submit(this.client.currentRoute(), "", 800, 800));
        this.properties.setQwenApiKey(null);
        assertThrows(ImageSubmissionRejectedException.class,
                () -> this.client.submit(this.client.currentRoute(), "封面", 800, 800));
        this.server.verify();
    }

    @Test
    void expiredTaskIsTerminalButNetworkFailureIsRetryableQuery() {
        this.server.expect(anything()).andRespond(withSuccess("{\"output\":{\"task_status\":\"UNKNOWN\"}}", MediaType.APPLICATION_JSON));
        this.server.expect(anything()).andRespond(withServerError());
        assertEquals(3, this.client.query(this.client.currentRoute(), "expired-id").state());
        assertThrows(BusinessException.class, () -> this.client.query(this.client.currentRoute(), "active-id"));
    }

    @Test
    void rejectsArbitraryEndpointBeforeSendingSecrets() {
        assertThrows(BusinessException.class, () -> this.client.requireConfigured(
                new ImageProviderRoute(1, "qwen-image-3.0", "https://dashscope.aliyuncs.com.attacker.example", "")));
        assertThrows(BusinessException.class, () -> this.client.requireConfigured(
                new ImageProviderRoute(1, "qwen-image-3.0", "http://127.0.0.1", "")));
        assertThrows(BusinessException.class, () -> this.client.requireConfigured(
                new ImageProviderRoute(1, "qwen-image-3.0", "https://dashscope.aliyuncs.com/path", "")));
        assertThrows(BusinessException.class, () -> this.client.query(this.client.currentRoute(), "../other"));
        this.server.verify();
    }

    @Test
    void signingIsDeterministicAndSensitiveToBody() {
        Instant timestamp = Instant.parse("2026-09-06T00:00:00Z");
        var headers = VolcImageSignUtil.headers("ak", "sk", "cn-north-1", "visual.volcengineapi.com",
                "Action=CVSync2AsyncSubmitTask&Version=2022-08-31", "{}", timestamp);
        assertEquals("20260906T000000Z", headers.get("X-Date"));
        assertEquals("44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a", headers.get("X-Content-Sha256"));
        assertEquals(headers, VolcImageSignUtil.headers("ak", "sk", "cn-north-1", "visual.volcengineapi.com",
                "Action=CVSync2AsyncSubmitTask&Version=2022-08-31", "{}", timestamp));
        assertNotEquals(headers.get("Authorization"), VolcImageSignUtil.headers("ak", "sk", "cn-north-1", "visual.volcengineapi.com",
                "Action=CVSync2AsyncSubmitTask&Version=2022-08-31", "{\"x\":1}", timestamp).get("Authorization"));
    }
}
