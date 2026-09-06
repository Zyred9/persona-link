package com.personalink.server.dto;

/** 计分维度响应。 */
public record ScoreDimensionResponse(
        Long id, Long versionId, String dimensionCode, String dimensionName, Integer sortNo) {
}
