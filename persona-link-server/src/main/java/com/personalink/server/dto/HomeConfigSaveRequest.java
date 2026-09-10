package com.personalink.server.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Objects;

/** 首页标题图配置；清空后回退小程序默认图。 */
public record HomeConfigSaveRequest(@Size(max = 1024, message = "首页标题图地址不能超过1024字符") String titleImageUrl) {
    public HomeConfigSaveRequest {
        titleImageUrl = Objects.isNull(titleImageUrl) ? "" : titleImageUrl.trim();
        if (titleImageUrl.regionMatches(true, 0, "https:", 0, 6)) {
            titleImageUrl = "https:" + titleImageUrl.substring(6);
        } else if (titleImageUrl.regionMatches(true, 0, "http:", 0, 5)) {
            titleImageUrl = "http:" + titleImageUrl.substring(5);
        }
    }

    @AssertTrue(message = "首页标题图须为HTTP(S)地址或/uploads/下的图片路径")
    public boolean isTitleImageUrlValid() {
        if (titleImageUrl.isEmpty()) { return true; }
        try {
            URI uri = URI.create(titleImageUrl);
            if (titleImageUrl.startsWith("/uploads/")) {
                return titleImageUrl.matches("/uploads/[A-Za-z0-9_-]+\\.(?i:png|jpg|jpeg|webp|gif)");
            }
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && Objects.nonNull(uri.getHost()) && Objects.isNull(uri.getUserInfo());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
