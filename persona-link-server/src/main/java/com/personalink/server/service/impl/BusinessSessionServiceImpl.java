package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalink.server.entity.BusinessSessionEntity;
import com.personalink.server.mapper.BusinessSessionMapper;
import com.personalink.server.service.BusinessSessionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 持久化业务会话服务实现。
 */
@Service
public class BusinessSessionServiceImpl extends ServiceImpl<BusinessSessionMapper, BusinessSessionEntity>
        implements BusinessSessionService {

    private static final int ADMIN_SESSION = 1;
    private static final int MINIAPP_SESSION = 2;
    private static final int NOT_DELETED = 0;

    @Override
    public BusinessSessionEntity findActiveAdminSession(String tokenHash, LocalDateTime currentTime) {
        return this.getOne(Wrappers.<BusinessSessionEntity>lambdaQuery()
                .eq(BusinessSessionEntity::getSessionTokenHash, tokenHash)
                .eq(BusinessSessionEntity::getSessionType, ADMIN_SESSION)
                .isNull(BusinessSessionEntity::getRevokedAt)
                .gt(BusinessSessionEntity::getExpiresAt, currentTime)
                .eq(BusinessSessionEntity::getDeleted, NOT_DELETED), false);
    }

    @Override
    public BusinessSessionEntity findActiveMiniappSession(String tokenHash, LocalDateTime currentTime) {
        return this.getOne(Wrappers.<BusinessSessionEntity>lambdaQuery()
                .eq(BusinessSessionEntity::getSessionTokenHash, tokenHash)
                .eq(BusinessSessionEntity::getSessionType, MINIAPP_SESSION)
                .isNull(BusinessSessionEntity::getRevokedAt)
                .gt(BusinessSessionEntity::getExpiresAt, currentTime)
                .eq(BusinessSessionEntity::getDeleted, NOT_DELETED), false);
    }

    @Override
    public void revoke(Long sessionId, LocalDateTime revokedAt) {
        this.lambdaUpdate()
                .set(BusinessSessionEntity::getRevokedAt, revokedAt)
                .eq(BusinessSessionEntity::getId, sessionId)
                .isNull(BusinessSessionEntity::getRevokedAt)
                .eq(BusinessSessionEntity::getDeleted, NOT_DELETED)
                .update();
    }

    @Override
    public void revokeAdminSessions(Long adminId, LocalDateTime revokedAt) {
        this.lambdaUpdate()
                .set(BusinessSessionEntity::getRevokedAt, revokedAt)
                .eq(BusinessSessionEntity::getAdminAccountId, adminId)
                .eq(BusinessSessionEntity::getSessionType, ADMIN_SESSION)
                .isNull(BusinessSessionEntity::getRevokedAt)
                .eq(BusinessSessionEntity::getDeleted, NOT_DELETED)
                .update();
    }
}
