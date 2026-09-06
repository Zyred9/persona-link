package com.personalink.server.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** 单人报告内容快照。 */
public record ReportSnapshotResponse(
        String resultCode,
        String resultName,
        JsonNode basicResult,
        JsonNode deepResult,
        JsonNode shareCopy,
        List<DimensionScoreResponse> dimensions) {
}
