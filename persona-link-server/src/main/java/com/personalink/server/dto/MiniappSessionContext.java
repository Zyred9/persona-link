package com.personalink.server.dto;

import com.personalink.server.entity.BusinessSessionEntity;

/**
 * 已校验的小程序会话上下文。
 *
 * @param openId 微信用户 OpenID
 * @param businessSession 未注销且未过期的小程序业务会话
 */
public record MiniappSessionContext(String openId, BusinessSessionEntity businessSession) {
}
