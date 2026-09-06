package com.personalink.server.service.impl;

import com.personalink.server.dto.AdminLoginRequest;
import com.personalink.server.dto.AdminLoginResponse;
import com.personalink.server.dto.AdminProfileResponse;
import com.personalink.server.dto.AdminSessionContext;
import com.personalink.server.entity.AdminAccountEntity;
import com.personalink.server.entity.BusinessSessionEntity;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.service.AdminAccountService;
import com.personalink.server.service.AdminAuthService;
import com.personalink.server.service.BusinessSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;

/**
 * 后台鉴权服务实现。
 */
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final int ENABLED = 1;
    private static final int ADMIN_SESSION = 1;
    private static final int READ_ONLY_ROLE = 3;
    private static final int ADMIN_ROLE = 1;
    private static final int NOT_DELETED = 0;
    private static final int TOKEN_BYTES = 32;
    private static final int SESSION_HOURS = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final AdminAccountService adminAccountService;
    private final BusinessSessionService businessSessionService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminAuthServiceImpl(AdminAccountService adminAccountService,
                                BusinessSessionService businessSessionService) {
        this.adminAccountService = adminAccountService;
        this.businessSessionService = businessSessionService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminLoginResponse login(AdminLoginRequest request) {
        AdminAccountEntity account = this.adminAccountService.findByUsername(request.username());
        if (Objects.isNull(account)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, 40101, "用户名或密码错误");
        }
        boolean passwordMatched = this.passwordEncoder.matches(request.password(), account.getPasswordHash());
        if (!passwordMatched || !Objects.equals(ENABLED, account.getStatus())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, 40101, "用户名或密码错误");
        }

        LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime expiresAt = currentTime.plusHours(SESSION_HOURS);
        String token = this.generateToken();

        BusinessSessionEntity session = new BusinessSessionEntity();
        session.setSessionTokenHash(this.hashToken(token));
        session.setSessionType(ADMIN_SESSION);
        session.setAdminAccountId(account.getId());
        session.setExpiresAt(expiresAt);
        session.setLastAccessAt(currentTime);
        session.setDeleted(NOT_DELETED);
        if (!this.adminAccountService.updateLastLoginAt(account.getId(), currentTime)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, 40101, "用户名或密码错误");
        }
        if (!this.businessSessionService.save(session)) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50001, "登录失败，请稍后重试");
        }

        return new AdminLoginResponse(token, account.getId(), account.getDisplayName(),
                account.getRoleType(), expiresAt);
    }

    @Override
    public AdminProfileResponse me(String authorization) {
        AdminAccountEntity account = this.requireSession(authorization).adminAccount();
        return new AdminProfileResponse(account.getId(), account.getDisplayName(), account.getRoleType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(String authorization) {
        AdminSessionContext context = this.requireSession(authorization);
        this.businessSessionService.revoke(context.businessSession().getId(), LocalDateTime.now());
    }

    @Override
    public AdminSessionContext requireSession(String authorization) {
        String token = this.extractBearerToken(authorization);
        BusinessSessionEntity session = this.businessSessionService.findActiveAdminSession(
                this.hashToken(token), LocalDateTime.now());
        if (Objects.isNull(session)) {
            throw this.unauthorizedSession();
        }

        AdminAccountEntity account = this.adminAccountService.findEnabledById(session.getAdminAccountId());
        if (Objects.isNull(account)) {
            throw this.unauthorizedSession();
        }
        return new AdminSessionContext(account, session);
    }

    @Override
    public AdminSessionContext requireWritableSession(String authorization) {
        AdminSessionContext context = this.requireSession(authorization);
        if (Objects.equals(READ_ONLY_ROLE, context.adminAccount().getRoleType())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "只读账号无权执行写操作");
        }
        return context;
    }

    @Override
    public AdminSessionContext requireAdminSession(String authorization) {
        AdminSessionContext context = this.requireSession(authorization);
        if (!Objects.equals(ADMIN_ROLE, context.adminAccount().getRoleType())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40302, "仅管理员可管理后台账号");
        }
        return context;
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

    private String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return TOKEN_ENCODER.encodeToString(tokenBytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    private BusinessException unauthorizedSession() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, 40102, "登录已失效，请重新登录");
    }
}
