package com.personalink.server.dto;

import java.util.List;

/** 回顾参与者，不向客户端暴露 OpenID 或计分规则。 */
public record AssessmentReviewParticipantResponse(
        String key, String label, String answerSessionId, String title,
        List<AssessmentQuestionResponse> questions) {
}
