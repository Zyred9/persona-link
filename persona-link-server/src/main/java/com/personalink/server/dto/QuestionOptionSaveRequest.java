package com.personalink.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 题目选项保存参数。 */
public record QuestionOptionSaveRequest(
        @Size(max = 16) String optionCode,
        @NotBlank @Size(max = 300) String optionText,
        Long dimensionId,
        @NotNull Integer scoreValue,
        Integer sortNo) {
}
