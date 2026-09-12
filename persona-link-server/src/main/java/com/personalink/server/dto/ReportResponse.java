package com.personalink.server.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/** 单人报告详情。 */
public record ReportResponse(
        String reportId,
        String answerSessionId,
        String resultCode,
        JsonNode resultSnapshot,
        LocalDateTime generatedAt,
        String shareToken) {
}
