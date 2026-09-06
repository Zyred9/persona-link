package com.personalink.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 创建 AI 题库生成任务参数。 */
public record AiGenerationStartRequest(
        @NotBlank @Size(max = 64) String requestId,
        @NotBlank @Size(max = 100) String testName,
        @NotNull @Min(1) @Max(2) Integer testType,
        @NotNull Long categoryId,
        @NotBlank @Size(max = 500) String coverUrl,
        @Size(max = 500) String detailImageUrl,
        String description,
        @NotNull @Min(1) Integer estimatedMinutes,
        @NotBlank @Size(max = 10000) String promptText,
        @NotNull @Min(10) @Max(500) Integer targetQuestionCount,
        @NotNull @Min(1) Integer drawQuestionCount) {
}
