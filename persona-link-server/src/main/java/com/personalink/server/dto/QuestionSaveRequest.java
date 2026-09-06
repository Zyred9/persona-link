package com.personalink.server.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 题目及选项整体保存参数。 */
public record QuestionSaveRequest(
        @NotNull @Min(1) @Max(2) Integer questionType,
        Long dimensionId,
        @NotNull @Min(1) Integer minSelectCount,
        @NotNull @Min(1) Integer maxSelectCount,
        @NotNull @Min(1) Integer questionNo,
        @NotBlank @Size(max = 500) String questionText,
        @NotNull @Min(0) @Max(1) Integer requiredFlag,
        Integer sortNo,
        @NotEmpty @Size(min = 2, max = 8) List<@Valid QuestionOptionSaveRequest> options) {
}
