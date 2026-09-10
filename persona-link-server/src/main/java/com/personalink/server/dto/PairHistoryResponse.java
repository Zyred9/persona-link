package com.personalink.server.dto;

import java.time.LocalDateTime;

/** 双人历史摘要，不包含参与者标识、邀请码及答案。 */
public record PairHistoryResponse(String pairSessionId, String title, String coverUrl, Integer versionNo,
                                  int pairStatus, LocalDateTime createdAt, LocalDateTime expiresAt) {
}
