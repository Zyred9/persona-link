package com.personalink.server.dto;

import java.time.LocalDateTime;

/**
 * 微信小程序登录响应。
 *
 * @param token 仅本次返回的业务会话明文令牌
 * @param expiresAt 会话过期时间
 */
public record WechatLoginResponse(String token, LocalDateTime expiresAt) {
}
