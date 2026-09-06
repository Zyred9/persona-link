package com.personalink.server.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.personalink.server.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.stream.Stream;

/** OSS 客户端统一配置；按需创建，应用关闭时释放连接。 */
@Configuration
public class OssConfiguration {

    private final String endpoint;
    private final String keyId;
    private final String keySecret;
    private final String bucketName;
    private final String domain;

    public OssConfiguration(@Value("${oss.endpoint:}") String endpoint,
                            @Value("${oss.keyId:}") String keyId,
                            @Value("${oss.keySecret:}") String keySecret,
                            @Value("${oss.bucketName:}") String bucketName,
                            @Value("${oss.domain:}") String domain) {
        this.endpoint = endpoint.trim();
        this.keyId = keyId.trim();
        this.keySecret = keySecret.trim();
        this.bucketName = bucketName.trim();
        this.domain = domain.trim().replaceAll("/+$", "");
    }

    @Bean(destroyMethod = "shutdown")
    @Lazy
    public OSS ossClient() {
        this.validate();
        return new OSSClientBuilder().build(this.endpoint, this.keyId, this.keySecret);
    }

    public boolean hasConfiguration() {
        return Stream.of(this.endpoint, this.keyId, this.keySecret, this.bucketName, this.domain)
                .anyMatch(value -> !value.isBlank());
    }

    public void validate() {
        if (Stream.of(this.endpoint, this.keyId, this.keySecret, this.bucketName, this.domain)
                .anyMatch(String::isBlank)) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50301, "OSS 配置不完整，请配置图片存储参数");
        }
        if (!this.isHttpUrl(this.endpoint) || !this.isHttpUrl(this.domain)
                || this.domain.length() + "/common/".length() + 8 + 1 + 32 + ".webp".length() > 500) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50301, "OSS endpoint 和 domain 必须是有效 HTTPS 地址，图片地址不能超过500字符");
        }
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && uri.getUserInfo() == null
                    && uri.getQuery() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public String getBucketName() {
        return this.bucketName;
    }

    public String getDomain() {
        return this.domain;
    }
}
