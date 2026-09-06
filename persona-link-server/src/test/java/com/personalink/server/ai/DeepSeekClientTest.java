package com.personalink.server.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalink.server.config.DeepSeekProperties;
import com.personalink.server.dto.AiGeneratedSetup;
import com.personalink.server.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DeepSeekProperties properties;
    private DeepSeekClient client;

    @BeforeEach
    void setUp() {
        this.properties = new DeepSeekProperties();
        this.client = new DeepSeekClient(this.properties, this.objectMapper);
    }

    @Test
    void parseResponseShouldReadJsonContent() throws Exception {
        String content = """
                {"dimensions":[{"dimensionCode":"D1","dimensionName":"外向","sortNo":0}],
                "resultRules":[{"dimensionCode":"D1","resultCode":"R1","resultName":"稳健",
                "scoreMin":0,"scoreMax":100,"basicResultJson":{"text":"结果"},"sortNo":0}]}
                """;
        ObjectNode root = this.objectMapper.createObjectNode();
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("finish_reason", "stop");
        choice.putObject("message").put("content", content);

        AiGeneratedSetup setup = this.client.parseResponse(
                this.objectMapper.writeValueAsString(root), AiGeneratedSetup.class);

        assertEquals("D1", setup.dimensions().get(0).dimensionCode());
        assertEquals("R1", setup.resultRules().get(0).resultCode());
    }

    @Test
    void parseResponseShouldRejectNonJsonContent() throws Exception {
        ObjectNode root = this.objectMapper.createObjectNode();
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("finish_reason", "stop");
        choice.putObject("message").put("content", "not-json");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> this.client.parseResponse(
                        this.objectMapper.writeValueAsString(root), AiGeneratedSetup.class));

        assertEquals(50232, exception.getCode());
    }

    @Test
    void parseResponseShouldRejectTruncatedContent() throws Exception {
        ObjectNode root = this.objectMapper.createObjectNode();
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("finish_reason", "length");
        choice.putObject("message").put("content", "{}");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> this.client.parseResponse(
                        this.objectMapper.writeValueAsString(root), AiGeneratedSetup.class));

        assertEquals(50232, exception.getCode());
    }

    @Test
    void requireConfiguredShouldRejectMissingApiKey() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> this.client.requireConfigured());

        assertEquals(50331, exception.getCode());
    }

    @Test
    void generateSetupShouldUseOfficialChatCompletionContract() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String content = """
                {"dimensions":[{"dimensionCode":"D1","dimensionName":"外向","sortNo":0}],
                "resultRules":[{"dimensionCode":"D1","resultCode":"R1","resultName":"稳健",
                "scoreMin":0,"scoreMax":100,"basicResultJson":{"text":"结果"},"sortNo":0}]}
                """;
        ObjectNode response = this.objectMapper.createObjectNode();
        ObjectNode choice = response.putArray("choices").addObject();
        choice.put("finish_reason", "stop");
        choice.putObject("message").put("content", content);
        byte[] responseBytes = this.objectMapper.writeValueAsBytes(response);
        server.createContext("/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.start();
        try {
            this.properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            this.properties.setApiKey("test-key");
            this.client = new DeepSeekClient(this.properties, this.objectMapper);

            AiGeneratedSetup setup = this.client.generateSetup(
                    "deepseek-v4-flash", "沟通方式", 2, "轻松、有趣",
                    "维度 D1 的结果区间存在断档或重叠");
            JsonNode body = this.objectMapper.readTree(requestBody.get());

            assertEquals("Bearer test-key", authorization.get());
            assertEquals("deepseek-v4-flash", body.path("model").asText());
            assertEquals("disabled", body.path("thinking").path("type").asText());
            assertEquals("json_object", body.path("response_format").path("type").asText());
            assertFalse(body.path("stream").asBoolean());
            assertTrue(body.path("messages").path(1).path("content").asText()
                    .contains("上一次生成结果未通过服务端校验"));
            assertEquals("D1", setup.dimensions().get(0).dimensionCode());
        } finally {
            server.stop(0);
        }
    }
}
