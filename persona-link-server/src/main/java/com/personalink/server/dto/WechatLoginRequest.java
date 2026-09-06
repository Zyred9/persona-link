package com.personalink.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 微信小程序登录请求。
 *
 * @param code wx.login 获取的一次性登录凭证
 */
public record WechatLoginRequest(
        @NotBlank(message = "不能为空")
        @Size(max = 128, message = "长度不能超过 128 个字符")
        String code) {
}
