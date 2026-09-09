package com.personalink.server.dto;
import java.time.LocalDateTime;
/** 协议；后台未配置类型的 version 为 0。 */
public record LegalDocumentResponse(int type, String title, String content, long version, LocalDateTime updatedAt) {}
