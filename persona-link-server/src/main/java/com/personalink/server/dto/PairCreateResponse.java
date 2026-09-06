package com.personalink.server.dto;

/** 创建双人邀请响应。 */
public record PairCreateResponse(
        PairSessionResponse pair,
        String inviteToken) {
}
