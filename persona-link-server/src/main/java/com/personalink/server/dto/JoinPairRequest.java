package com.personalink.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 加入双人邀请请求。 */
public record JoinPairRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9]{5}") String inviteToken,
        @NotBlank @Size(max = 64) String createRequestId) {
}
