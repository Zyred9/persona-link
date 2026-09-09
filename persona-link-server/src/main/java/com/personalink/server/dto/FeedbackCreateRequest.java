package com.personalink.server.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
/** 反馈提交；重试保持 requestId 不变。 */
public record FeedbackCreateRequest(@NotBlank @Size(max=64) String requestId,
        @NotBlank @Size(max=500) String content) {
    public FeedbackCreateRequest {
        requestId = requestId == null ? null : requestId.trim();
        content = content == null ? null : content.trim();
    }
}
