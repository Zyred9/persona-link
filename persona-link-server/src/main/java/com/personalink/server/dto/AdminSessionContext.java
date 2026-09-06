package com.personalink.server.dto;

import com.personalink.server.entity.AdminAccountEntity;
import com.personalink.server.entity.BusinessSessionEntity;

/**
 * 已校验的后台会话上下文。
 *
 * @param adminAccount 启用中的后台账号
 * @param businessSession 未注销且未过期的后台会话
 */
public record AdminSessionContext(
        AdminAccountEntity adminAccount,
        BusinessSessionEntity businessSession) {
}
