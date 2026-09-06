package com.personalink.server.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.entity.AdminAccountEntity;
import com.personalink.server.dto.AdminAccountCreateRequest;
import com.personalink.server.dto.AdminAccountQuery;
import com.personalink.server.dto.AdminAccountResponse;
import com.personalink.server.dto.AdminAccountUpdateRequest;
import com.personalink.server.dto.PageResponse;

import java.time.LocalDateTime;

/**
 * 后台账号服务。
 */
public interface AdminAccountService extends IService<AdminAccountEntity> {

    AdminAccountEntity findByUsername(String username);

    AdminAccountEntity findEnabledById(Long adminId);

    boolean updateLastLoginAt(Long adminId, LocalDateTime lastLoginAt);

    PageResponse<AdminAccountResponse> pageAccounts(AdminAccountQuery query);

    AdminAccountResponse createAccount(AdminAccountCreateRequest request);

    AdminAccountResponse updateAccount(Long id, AdminAccountUpdateRequest request, Long operatorId);

    void resetPassword(Long id, String password);
}
