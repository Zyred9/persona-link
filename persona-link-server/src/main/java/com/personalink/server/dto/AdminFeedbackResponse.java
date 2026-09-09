package com.personalink.server.dto;

import java.time.LocalDateTime;

/** 管理员反馈列表记录，包含提交人的小程序用户标识。 */
public record AdminFeedbackResponse(String id, String openId, String content, LocalDateTime createdAt) {}
