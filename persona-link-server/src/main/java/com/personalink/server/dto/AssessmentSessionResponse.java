package com.personalink.server.dto;

import java.util.List;

/** 答卷续答视图。 */
public record AssessmentSessionResponse(
        String answerSessionId,
        String testId,
        String versionId,
        String title,
        int answerStatus,
        int totalCount,
        int completedCount,
        int firstUnansweredIndex,
        List<AssessmentQuestionResponse> questions,
        int answerType,
        String pairSessionId,
        boolean canRestart) {
}
