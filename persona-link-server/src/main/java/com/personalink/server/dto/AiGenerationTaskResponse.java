package com.personalink.server.dto;

import java.time.LocalDateTime;

/** AI 题库生成任务响应。 */
public record AiGenerationTaskResponse(
        Long id,
        String taskNo,
        Long testId,
        Long versionId,
        String testName,
        Integer testType,
        Long categoryId,
        String coverUrl,
        String detailImageUrl,
        String description,
        Integer estimatedMinutes,
        Integer drawQuestionCount,
        String modelName,
        Integer targetQuestionCount,
        Integer generatedQuestionCount,
        Integer currentBatchNo,
        Integer totalBatchCount,
        Integer completedBatchCount,
        Integer retryCount,
        Integer taskStatus,
        String errorMessage,
        LocalDateTime completedAt,
        LocalDateTime submittedAt) {
}
