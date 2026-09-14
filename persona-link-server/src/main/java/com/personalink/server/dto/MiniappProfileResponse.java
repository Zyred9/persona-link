package com.personalink.server.dto;

/**
 * 当前用户本人资料（不含 OpenID）。
 *
 * @param nickname       头像昵称均由用户提供时为用户填写值，否则为默认值
 * @param avatarUrl      用户未提供头像时为默认头像或空串
 * @param customized     头像与昵称是否均为用户本人提供（默认值不算提供）
 * @param avatarReviewing 是否有头像正在内容安全审核中，审核通过前展示旧头像
 */
public record MiniappProfileResponse(String nickname, String avatarUrl, boolean customized, boolean avatarReviewing) {
}
