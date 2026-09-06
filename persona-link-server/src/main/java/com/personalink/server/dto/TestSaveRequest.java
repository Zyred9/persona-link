package com.personalink.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 题型保存参数。 */
public record TestSaveRequest(
        @NotBlank @Size(max = 100) String testName,
        @NotNull @Min(1) @Max(2) Integer testType,
        @NotNull Long categoryId,
        @NotNull @Min(0) @Max(1) Integer status) {
}
