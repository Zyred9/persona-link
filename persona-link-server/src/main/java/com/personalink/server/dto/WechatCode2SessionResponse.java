package com.personalink.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 微信 code2Session 最小响应，仅接收业务所需字段。
 *
 * @param openId 微信用户 OpenID
 * @param errorCode 微信错误码
 */
public record WechatCode2SessionResponse(
        @JsonProperty("openid") String openId,
        @JsonProperty("errcode") Integer errorCode) {
}
