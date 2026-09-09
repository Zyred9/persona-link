package com.personalink.server.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
/** 保存即发布的纯文本协议。 */
public record LegalDocumentSaveRequest(@NotBlank @Size(max=100) String title,
        @NotBlank @Size(max=30000) String content) {
    public LegalDocumentSaveRequest {
        title = title == null ? null : title.trim();
        content = content == null ? null : content.trim();
    }
}
