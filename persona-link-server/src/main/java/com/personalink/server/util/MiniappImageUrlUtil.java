package com.personalink.server.util;

import java.net.URI;
import java.util.Objects;

/** 小程序展示图片地址的归一化与校验；空值表示由调用方回退默认展示。 */
public final class MiniappImageUrlUtil {
    private MiniappImageUrlUtil() { }

    /** 去除首尾空白并把协议头统一为小写；空值返回空字符串。 */
    public static String normalize(String imageUrl) {
        String value = Objects.isNull(imageUrl) ? "" : imageUrl.trim();
        if (value.regionMatches(true, 0, "https:", 0, 6)) {
            return "https:" + value.substring(6);
        }
        if (value.regionMatches(true, 0, "http:", 0, 5)) {
            return "http:" + value.substring(5);
        }
        return value;
    }

    /** 仅接受 HTTP(S) 图片地址或 /uploads/ 下的图片路径，空值视为有效。 */
    public static boolean isValid(String imageUrl) {
        String value = normalize(imageUrl);
        if (value.isEmpty()) {
            return true;
        }
        try {
            URI uri = URI.create(value);
            if (value.startsWith("/uploads/")) {
                return value.matches("/uploads/[A-Za-z0-9_-]+\\.(?i:png|jpg|jpeg|webp|gif)");
            }
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && Objects.nonNull(uri.getHost()) && Objects.isNull(uri.getUserInfo());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
