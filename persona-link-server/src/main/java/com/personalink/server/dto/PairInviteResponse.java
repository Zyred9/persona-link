package com.personalink.server.dto;

import java.time.LocalDateTime;

/**
 * 邀请码预览：当前用户与该邀请的关系，用于分享消息被重复点击时的自动分流。
 *
 * @param pairSessionId   配对会话 ID，仅参与者可见，非参与者为 null
 * @param pairStatus      配对状态，参见 PairStatus
 * @param myRole          当前用户角色，INITIATOR / PARTNER，非参与者为 null
 * @param answerSessionId 当前用户在该配对中的答卷 ID，非参与者为 null
 * @param joinable        当前用户是否还能加入该邀请
 * @param expiresAt       邀请过期时间
 */
public record PairInviteResponse(
        String pairSessionId,
        int pairStatus,
        String myRole,
        String answerSessionId,
        boolean joinable,
        LocalDateTime expiresAt) {
}
