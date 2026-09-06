package com.personalink.server.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;

/** 火山视觉服务 V4 签名；只签固定 POST / 与 Action、Version 查询串。 */
public final class VolcImageSignUtil {
    private VolcImageSignUtil() { }

    public static Map<String, String> headers(String accessKey, String secretKey, String region,
                                               String host, String query, String body, Instant now) {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC).format(now);
        String day = timestamp.substring(0, 8);
        String payloadHash = sha256(body);
        String names = "content-type;host;x-content-sha256;x-date";
        String canonicalHeaders = "content-type:application/json\nhost:" + host
                + "\nx-content-sha256:" + payloadHash + "\nx-date:" + timestamp + "\n";
        String canonical = "POST\n/\n" + query + "\n" + canonicalHeaders + "\n" + names + "\n" + payloadHash;
        String scope = day + "/" + region + "/cv/request";
        String signingText = "HMAC-SHA256\n" + timestamp + "\n" + scope + "\n" + sha256(canonical);
        byte[] key = hmac(secretKey.getBytes(StandardCharsets.UTF_8), day);
        key = hmac(key, region);
        key = hmac(key, "cv");
        key = hmac(key, "request");
        String authorization = "HMAC-SHA256 Credential=" + accessKey + "/" + scope
                + ", SignedHeaders=" + names + ", Signature=" + HexFormat.of().formatHex(hmac(key, signingText));
        return Map.of("Content-Type", "application/json", "X-Date", timestamp,
                "X-Content-Sha256", payloadHash, "Authorization", authorization);
    }

    private static byte[] hmac(byte[] key, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("火山生图签名计算失败", exception);
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("火山生图摘要计算失败", exception);
        }
    }
}
