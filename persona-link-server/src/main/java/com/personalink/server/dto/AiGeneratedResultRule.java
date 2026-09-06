package com.personalink.server.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/** DeepSeek 生成的结果规则。 */
public record AiGeneratedResultRule(
        String dimensionCode,
        String resultCode,
        String resultName,
        BigDecimal scoreMin,
        BigDecimal scoreMax,
        JsonNode basicResultJson,
        JsonNode deepResultJson,
        JsonNode shareCopyJson,
        Integer sortNo) {
}
