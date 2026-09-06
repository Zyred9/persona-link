package com.personalink.server.service;

import com.personalink.server.dto.AdminLoginRequest;
import com.personalink.server.dto.AdminLoginResponse;
import com.personalink.server.dto.AdminProfileResponse;
import com.personalink.server.dto.AdminSessionContext;

/**
 * 后台鉴权服务。
 */
public interface AdminAuthService {

    AdminLoginResponse login(AdminLoginRequest request);

    AdminProfileResponse me(String authorization);

    void logout(String authorization);

    AdminSessionContext requireSession(String authorization);

    AdminSessionContext requireWritableSession(String authorization);

    AdminSessionContext requireAdminSession(String authorization);
}
