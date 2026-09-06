package com.personalink.server.service.impl;

import com.personalink.server.entity.BusinessSessionEntity;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.dto.MiniappSessionContext;
import com.personalink.server.miniapp.auth.MiniappTokenCodec;
import com.personalink.server.miniapp.auth.WechatCode2SessionClient;
import com.personalink.server.dto.WechatLoginRequest;
import com.personalink.server.dto.WechatLoginResponse;
import com.personalink.server.service.BusinessSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 小程序登录与业务会话校验服务。
 */
@Service
public class AuthService {

    private static final int SESSION = 2;
    private static final int NOT_DELETED = 0;
    private static final int SESSION_DAYS = 30;

    private final WechatCode2SessionClient wechatCode2SessionClient;
    private final BusinessSessionService businessSessionService;

    public AuthService(WechatCode2SessionClient wechatCode2SessionClient,
                       BusinessSessionService businessSessionService) {
        this.wechatCode2SessionClient = wechatCode2SessionClient;
        this.businessSessionService = businessSessionService;
    }

    public WechatLoginResponse login(WechatLoginRequest request) {
        String openId = this.wechatCode2SessionClient.exchange(request.code());
        LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime expiresAt = currentTime.plusDays(SESSION_DAYS);
        String token = MiniappTokenCodec.generateToken();

        BusinessSessionEntity session = new BusinessSessionEntity();
        session.setSessionTokenHash(MiniappTokenCodec.hashToken(token));
        session.setSessionType(SESSION);
        session.setOpenId(openId);
        session.setExpiresAt(expiresAt);
        session.setLastAccessAt(currentTime);
        session.setDeleted(NOT_DELETED);
        if (!this.businessSessionService.save(session)) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50002, "登录失败，请稍后重试");
        }
        return new WechatLoginResponse(token, expiresAt);
    }

    public MiniappSessionContext requireSession(String authorization) {
        String token = this.extractBearerToken(authorization);
        BusinessSessionEntity session = this.businessSessionService.findActiveMiniappSession(
                MiniappTokenCodec.hashToken(token), LocalDateTime.now());
        if (Objects.isNull(session) || Objects.isNull(session.getOpenId())) {
            throw this.unauthorizedSession();
        }
        return new MiniappSessionContext(session.getOpenId(), session);
    }

    private String extractBearerToken(String authorization) {
        if (Objects.isNull(authorization)
                || !authorization.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            throw this.unauthorizedSession();
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw this.unauthorizedSession();
        }
        return token;
    }

    private BusinessException unauthorizedSession() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, 40102, "登录已失效，请重新登录");
    }
}
