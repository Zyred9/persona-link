package com.personalink.server.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 计分维度保存参数。 */
public record ScoreDimensionSaveRequest(
        Long id,
        @Size(max = 32) String dimensionCode,
        @NotBlank @Size(max = 64) String dimensionName,
        @NotNull @Min(0) Integer sortNo) {
}
