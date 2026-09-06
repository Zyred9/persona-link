package com.personalink.server.dto;

import java.time.LocalDateTime;

/** 内容审计响应。 */
public record AuditLogResponse(
        Long id,
        Integer bizType,
        Long bizId,
        Integer actionType,
        String beforeSnapshot,
        String afterSnapshot,
        String reason,
        Long operatorId,
        LocalDateTime createDate) {
}
