package com.personalink.server.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/** 结果模板响应。 */
public record ResultTemplateResponse(
        Long id,
        Long dimensionId,
        String resultCode,
        String resultName,
        BigDecimal scoreMin,
        BigDecimal scoreMax,
        JsonNode basicResultJson,
        JsonNode deepResultJson,
        JsonNode shareCopyJson,
        Integer sortNo) {
}
