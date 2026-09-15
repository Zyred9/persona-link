package com.personalink.server.miniapp;

import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 微信 API RestClient 构建工厂。
 * <p>微信接口会校验请求体长度：Spring 6.1+ 默认 JDK HttpClient 流式发送 POST
 * 时使用 Transfer-Encoding: chunked（无 Content-Length），会被微信以
 * 412 Precondition Failed 拒绝；统一包装缓冲请求工厂，让请求体落内存并携带 Content-Length。
 */
public final class WechatRestClientFactory {

    private static final ClientHttpRequestFactory REQUEST_FACTORY =
            new BufferingClientHttpRequestFactory(new JdkClientHttpRequestFactory());

    private WechatRestClientFactory() {
    }

    /** 返回对接微信 API 的 RestClient Builder。 */
    public static RestClient.Builder builder() {
        return RestClient.builder().requestFactory(REQUEST_FACTORY);
    }
}
