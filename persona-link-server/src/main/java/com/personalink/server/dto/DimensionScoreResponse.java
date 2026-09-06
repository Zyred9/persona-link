package com.personalink.server.dto;

import java.math.BigDecimal;

/** 报告维度计分快照。 */
public record DimensionScoreResponse(
        String dimensionId,
        String dimensionCode,
        String dimensionName,
        BigDecimal rawScore,
        BigDecimal theoreticalMin,
        BigDecimal theoreticalMax,
        BigDecimal normalizedScore) {
}
