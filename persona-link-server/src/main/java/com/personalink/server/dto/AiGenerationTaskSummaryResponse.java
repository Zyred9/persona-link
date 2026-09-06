package com.personalink.server.dto;

import java.time.LocalDateTime;

/** AI 题库任务列表项。 */
public record AiGenerationTaskSummaryResponse(
        Long id,
        String taskNo,
        String testName,
        Integer targetQuestionCount,
        Integer generatedQuestionCount,
        Integer taskStatus,
        String errorMessage,
        Long operatorId,
        LocalDateTime createDate,
        LocalDateTime completedAt,
        LocalDateTime submittedAt) {
}
