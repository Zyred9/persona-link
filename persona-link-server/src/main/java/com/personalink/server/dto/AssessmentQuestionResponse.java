package com.personalink.server.dto;

import java.util.List;

/** 答卷题目及已保存答案。 */
public record AssessmentQuestionResponse(
        String questionId,
        int questionType,
        String questionText,
        boolean required,
        int minSelectCount,
        int maxSelectCount,
        List<AssessmentQuestionOptionResponse> options,
        List<String> selectedOptionIds) {
}
