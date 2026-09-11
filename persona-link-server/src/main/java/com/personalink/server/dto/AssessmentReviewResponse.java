package com.personalink.server.dto;

import java.util.List;

/** 已提交答卷的只读回顾，仅包含当前用户有权查看的参与者。 */
public record AssessmentReviewResponse(List<AssessmentReviewParticipantResponse> participants) {
}
