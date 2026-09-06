package com.personalink.server.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 题型版本详情及列表响应。 */
public record TestVersionResponse(
        Long id,
        Long testId,
        Integer versionNo,
        String title,
        String coverUrl,
        String detailImageUrl,
        String description,
        Integer estimatedMinutes,
        Integer drawQuestionCount,
        Integer versionStatus,
        String versionNote,
        LocalDateTime scheduledAt,
        LocalDateTime publishedAt,
        LocalDateTime offlineAt,
        long questionCount,
        List<ScoreDimensionResponse> dimensions) {
}
