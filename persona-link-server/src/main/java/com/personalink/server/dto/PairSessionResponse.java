package com.personalink.server.dto;

import java.time.LocalDateTime;

/** 双人配对状态，不包含双方逐题答案。 */
public record PairSessionResponse(
        String pairSessionId,
        int pairStatus,
        String myRole,
        String initiatorAnswerSessionId,
        String partnerAnswerSessionId,
        String pairReportId,
        LocalDateTime expiresAt) {
}
