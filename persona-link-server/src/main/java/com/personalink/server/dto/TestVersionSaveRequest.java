package com.personalink.server.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 题型版本保存参数。 */
public record TestVersionSaveRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 500) String coverUrl,
        @Size(max = 500) String detailImageUrl,
        String description,
        @NotNull @Min(1) Integer estimatedMinutes,
        @NotNull @Min(1) Integer drawQuestionCount,
        @Size(max = 500) String versionNote,
        @NotEmpty List<@Valid ScoreDimensionSaveRequest> dimensions) {
}
