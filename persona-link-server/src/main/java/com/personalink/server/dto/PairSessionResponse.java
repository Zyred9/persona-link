package com.personalink.server.dto;

import java.time.LocalDateTime;

/**
 * 双人配对状态，不包含双方逐题答案。
 *
 * @param inviteToken 仅发起者处于等待加入状态时返回，用于重新分享邀请；其余情况为 null
 */
public record PairSessionResponse(
        String pairSessionId,
        int pairStatus,
        String myRole,
        String initiatorAnswerSessionId,
        String partnerAnswerSessionId,
        String pairReportId,
        LocalDateTime expiresAt,
        String inviteToken) {
}
