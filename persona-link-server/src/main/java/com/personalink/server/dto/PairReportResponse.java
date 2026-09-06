package com.personalink.server.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/** 双人聚合报告详情。 */
public record PairReportResponse(
        String reportId,
        String pairSessionId,
        JsonNode resultSnapshot,
        LocalDateTime generatedAt) {
}
