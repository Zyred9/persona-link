package com.personalink.server.dto;

import java.time.LocalDateTime;

/** 管理员反馈列表记录；提交人标识与昵称在匿名提交时为空。 */
public record AdminFeedbackResponse(String id, String openId, String nickname, String content, LocalDateTime createdAt) {}
