package com.personalink.server.miniapp;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatRestClientFactoryTest {

    private HttpServer server;
    private int port;
    private final CountDownLatch received = new CountDownLatch(1);
    private final AtomicReference<String> transferEncoding = new AtomicReference<>();
    private final AtomicReference<String> contentLength = new AtomicReference<>();

    @BeforeEach
    void setup() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(0), 0);
        this.server.createContext("/probe", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                in.readAllBytes();
            }
            this.transferEncoding.set(exchange.getRequestHeaders().getFirst("Transfer-encoding"));
            this.contentLength.set(exchange.getRequestHeaders().getFirst("Content-length"));
            byte[] body = "{\"errcode\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            this.received.countDown();
        });
        this.server.start();
        this.port = this.server.getAddress().getPort();
    }

    @AfterEach
    void teardown() {
        this.server.stop(0);
    }

    @Test
    void sendsContentLengthInsteadOfChunkedEncoding() throws Exception {
        RestClient client = WechatRestClientFactory.builder()
                .baseUrl("http://localhost:" + this.port)
                .build();

        client.post().uri("/probe")
                .body(Map.of("content", "hello", "version", 2))
                .retrieve().body(String.class);

        assertTrue(this.received.await(5, TimeUnit.SECONDS), "本地探测服务未收到请求");
        assertNull(this.transferEncoding.get(), "微信接口不接受 chunked 请求体");
        assertNotNull(this.contentLength.get(), "POST 请求必须携带 Content-Length");
    }
}
