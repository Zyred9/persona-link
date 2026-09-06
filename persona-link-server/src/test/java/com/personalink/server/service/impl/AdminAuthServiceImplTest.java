package com.personalink.server.service.impl;

import com.personalink.server.dto.AdminLoginRequest;
import com.personalink.server.dto.AdminLoginResponse;
import com.personalink.server.entity.AdminAccountEntity;
import com.personalink.server.entity.BusinessSessionEntity;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.service.AdminAccountService;
import com.personalink.server.service.BusinessSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceImplTest {

    @Mock
    private AdminAccountService adminAccountService;
    @Mock
    private BusinessSessionService businessSessionService;

    private AdminAuthServiceImpl adminAuthService;

    @BeforeEach
    void setUp() {
        this.adminAuthService = new AdminAuthServiceImpl(this.adminAccountService, this.businessSessionService);
    }

    @Test
    void shouldStoreOnlyTokenHashAndRevokeCurrentSession() throws Exception {
        AdminAccountEntity account = this.enabledAccount("correct-password");
        when(this.adminAccountService.findByUsername("admin")).thenReturn(account);
        when(this.adminAccountService.updateLastLoginAt(any(Long.class), any(LocalDateTime.class)))
                .thenReturn(true);
        when(this.businessSessionService.save(any(BusinessSessionEntity.class))).thenReturn(true);

        AdminLoginResponse response = this.adminAuthService.login(
                new AdminLoginRequest("admin", "correct-password"));

        ArgumentCaptor<BusinessSessionEntity> sessionCaptor = ArgumentCaptor.forClass(BusinessSessionEntity.class);
        verify(this.businessSessionService).save(sessionCaptor.capture());
        BusinessSessionEntity savedSession = sessionCaptor.getValue();
        assertNotEquals(response.token(), savedSession.getSessionTokenHash());
        assertEquals(this.sha256(response.token()), savedSession.getSessionTokenHash());

        savedSession.setId(10L);
        when(this.businessSessionService.findActiveAdminSession(anyString(), any(LocalDateTime.class)))
                .thenReturn(savedSession);
        when(this.adminAccountService.findEnabledById(1L)).thenReturn(account);
        this.adminAuthService.logout("Bearer " + response.token());
        verify(this.businessSessionService).revoke(any(Long.class), any(LocalDateTime.class));
    }

    @Test
    void shouldRejectWrongPasswordWithoutCreatingSession() {
        when(this.adminAccountService.findByUsername("admin"))
                .thenReturn(this.enabledAccount("correct-password"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> this.adminAuthService.login(new AdminLoginRequest("admin", "wrong-password")));

        assertEquals(401, exception.getHttpStatus().value());
        assertEquals("用户名或密码错误", exception.getMessage());
        verify(this.businessSessionService, never()).save(any(BusinessSessionEntity.class));
    }

    @Test
    void shouldRejectLoginWhenSessionCannotBeStored() {
        AdminAccountEntity account = this.enabledAccount("correct-password");
        when(this.adminAccountService.findByUsername("admin")).thenReturn(account);
        when(this.adminAccountService.updateLastLoginAt(any(Long.class), any(LocalDateTime.class)))
                .thenReturn(true);
        when(this.businessSessionService.save(any(BusinessSessionEntity.class))).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> this.adminAuthService.login(new AdminLoginRequest("admin", "correct-password")));

        assertEquals(500, exception.getHttpStatus().value());
    }

    @Test
    void shouldRejectWriteForReadOnlyAccount() {
        AdminAccountEntity account = this.enabledAccount("correct-password");
        account.setRoleType(3);
        BusinessSessionEntity session = new BusinessSessionEntity();
        session.setId(10L);
        session.setAdminAccountId(account.getId());
        when(this.businessSessionService.findActiveAdminSession(anyString(), any(LocalDateTime.class)))
                .thenReturn(session);
        when(this.adminAccountService.findEnabledById(account.getId())).thenReturn(account);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> this.adminAuthService.requireWritableSession("Bearer valid-token"));

        assertEquals(403, exception.getHttpStatus().value());
    }

    private AdminAccountEntity enabledAccount(String password) {
        AdminAccountEntity account = new AdminAccountEntity();
        account.setId(1L);
        account.setUsername("admin");
        account.setPasswordHash(new BCryptPasswordEncoder().encode(password));
        account.setDisplayName("管理员");
        account.setRoleType(1);
        account.setStatus(1);
        account.setDeleted(0);
        return account;
    }

    private String sha256(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
