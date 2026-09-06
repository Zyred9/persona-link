package com.personalink.server.dto;

import java.time.LocalDateTime;

/**
 * 后台登录响应。
 *
 * @param token 仅返回客户端的会话令牌明文
 * @param adminId 后台账号 Long 主键
 * @param displayName 显示名称
 * @param roleType 角色类型：1管理员，2内容运营，3只读查看
 * @param expiresAt 会话过期时间
 */
public record AdminLoginResponse(
        String token,
        Long adminId,
        String displayName,
        Integer roleType,
        LocalDateTime expiresAt) {
}
