package com.personalink.server.dto;

/** 当前用户公开资料，不返回 OpenID。 */
public record MiniappProfileResponse(String nickname, String avatarUrl) {
}
