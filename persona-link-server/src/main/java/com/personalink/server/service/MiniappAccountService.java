package com.personalink.server.service;

/**
 * 小程序账号注销服务。
 * <p>注销为不可恢复操作：删除该账号的全部个人数据并失效所有会话。
 * @author persona-link
 * @since 1.0.0
 */
public interface MiniappAccountService {

    /**
     * 注销指定用户账号。
     * @param openId 当前会话对应的微信 OpenID
     */
    void cancel(String openId);
}
