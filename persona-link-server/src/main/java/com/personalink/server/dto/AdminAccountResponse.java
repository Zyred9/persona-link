package com.personalink.server.dto;

import java.time.LocalDateTime;

/** 后台账号响应。 */
public record AdminAccountResponse(
        Long id,
        String username,
        String displayName,
        Integer roleType,
        Integer status,
        LocalDateTime lastLoginAt,
        LocalDateTime createDate) {
}
