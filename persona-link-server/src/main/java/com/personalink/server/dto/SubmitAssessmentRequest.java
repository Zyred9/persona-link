package com.personalink.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 提交答卷请求。 */
public record SubmitAssessmentRequest(
        @NotBlank @Size(max = 64) String submitRequestId) {
}
