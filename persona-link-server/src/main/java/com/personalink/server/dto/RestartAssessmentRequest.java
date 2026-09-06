package com.personalink.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 重新开始答卷请求。 */
public record RestartAssessmentRequest(
        @NotBlank @Size(max = 64) String createRequestId) {
}
