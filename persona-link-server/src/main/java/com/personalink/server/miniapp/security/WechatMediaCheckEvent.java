package com.personalink.server.miniapp.security;

/**
 * 微信图片内容安全异步审核结果事件。
 *
 * @param openId 提交图片的用户 OpenID
 * @param traceId 微信审核任务号，用于关联待审头像
 * @param passed 微信结论是否明确通过
 * @author persona-link
 * @since 1.0.0
 */
public record WechatMediaCheckEvent(String openId, String traceId, boolean passed) {
}
