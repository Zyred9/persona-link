package com.personalink.server.dto;
import java.time.LocalDateTime;
/** 小程序反馈收件确认，不返回用户标识。 */
public record FeedbackResponse(String id, String content, LocalDateTime createdAt) {}
