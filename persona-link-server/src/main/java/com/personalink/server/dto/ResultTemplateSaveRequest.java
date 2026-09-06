package com.personalink.server.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 结果模板保存参数。 */
public record ResultTemplateSaveRequest(
        Long id,
        Long dimensionId,
        @Size(max = 32) String resultCode,
        @NotBlank @Size(max = 100) String resultName,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal scoreMin,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal scoreMax,
        @NotNull JsonNode basicResultJson,
        JsonNode deepResultJson,
        JsonNode shareCopyJson,
        Integer sortNo) {
}
