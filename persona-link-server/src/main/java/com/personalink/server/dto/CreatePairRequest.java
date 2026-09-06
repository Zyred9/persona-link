package com.personalink.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 创建双人邀请请求。 */
public record CreatePairRequest(
        @NotBlank @Pattern(regexp = "[1-9]\\d{0,19}") String answerSessionId,
        @NotBlank @Size(max = 64) String createRequestId) {
}
