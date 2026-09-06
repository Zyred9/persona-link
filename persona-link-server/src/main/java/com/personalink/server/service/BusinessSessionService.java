package com.personalink.server.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.entity.BusinessSessionEntity;

import java.time.LocalDateTime;

/**
 * 持久化业务会话服务。
 */
public interface BusinessSessionService extends IService<BusinessSessionEntity> {

    BusinessSessionEntity findActiveAdminSession(String tokenHash, LocalDateTime currentTime);

    BusinessSessionEntity findActiveMiniappSession(String tokenHash, LocalDateTime currentTime);

    void revoke(Long sessionId, LocalDateTime revokedAt);

    void revokeAdminSessions(Long adminId, LocalDateTime revokedAt);
}
