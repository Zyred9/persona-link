package com.personalink.server.dto;

import java.time.LocalDateTime;

/** 历史报告列表项。 */
public record ReportHistoryResponse(
        String reportId,
        String answerSessionId,
        String testId,
        int versionNo,
        String title,
        String resultCode,
        String resultName,
        LocalDateTime generatedAt) {
}
