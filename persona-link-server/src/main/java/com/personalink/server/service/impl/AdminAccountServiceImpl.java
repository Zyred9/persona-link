package com.personalink.server.service.impl;

import lombok.RequiredArgsConstructor;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalink.server.entity.AdminAccountEntity;
import com.personalink.server.dto.AdminAccountCreateRequest;
import com.personalink.server.dto.AdminAccountQuery;
import com.personalink.server.dto.AdminAccountResponse;
import com.personalink.server.dto.AdminAccountUpdateRequest;
import com.personalink.server.dto.PageResponse;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.AdminAccountMapper;
import com.personalink.server.service.AdminAccountService;
import com.personalink.server.service.BusinessSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 后台账号服务实现。
 */
@Service
@RequiredArgsConstructor
public class AdminAccountServiceImpl extends ServiceImpl<AdminAccountMapper, AdminAccountEntity>
        implements AdminAccountService {

    private static final int ENABLED = 1;
    private static final int NOT_DELETED = 0;
    private final BusinessSessionService businessSessionService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public AdminAccountEntity findByUsername(String username) {
        return this.getOne(Wrappers.<AdminAccountEntity>lambdaQuery()
                .eq(AdminAccountEntity::getUsername, username)
                .eq(AdminAccountEntity::getDeleted, NOT_DELETED), false);
    }

    @Override
    public AdminAccountEntity findEnabledById(Long adminId) {
        return this.getOne(Wrappers.<AdminAccountEntity>lambdaQuery()
                .eq(AdminAccountEntity::getId, adminId)
                .eq(AdminAccountEntity::getStatus, ENABLED)
                .eq(AdminAccountEntity::getDeleted, NOT_DELETED), false);
    }

    @Override
    public boolean updateLastLoginAt(Long adminId, LocalDateTime lastLoginAt) {
        return this.lambdaUpdate()
                .set(AdminAccountEntity::getLastLoginAt, lastLoginAt)
                .eq(AdminAccountEntity::getId, adminId)
                .eq(AdminAccountEntity::getStatus, ENABLED)
                .eq(AdminAccountEntity::getDeleted, NOT_DELETED)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminAccountResponse> pageAccounts(AdminAccountQuery query) {
        boolean hasKeyword = Objects.nonNull(query.getKeyword()) && !query.getKeyword().isBlank();
        long total = this.lambdaQuery()
                .and(hasKeyword, wrapper -> wrapper.like(AdminAccountEntity::getUsername, query.getKeyword().trim())
                        .or().like(AdminAccountEntity::getDisplayName, query.getKeyword().trim()))
                .eq(Objects.nonNull(query.getRoleType()), AdminAccountEntity::getRoleType, query.getRoleType())
                .eq(Objects.nonNull(query.getStatus()), AdminAccountEntity::getStatus, query.getStatus())
                .eq(AdminAccountEntity::getDeleted, NOT_DELETED)
                .count();
        long offset = (query.getPage() - 1) * query.getSize();
        List<AdminAccountResponse> records = this.lambdaQuery()
                .and(hasKeyword, wrapper -> wrapper.like(AdminAccountEntity::getUsername, query.getKeyword().trim())
                        .or().like(AdminAccountEntity::getDisplayName, query.getKeyword().trim()))
                .eq(Objects.nonNull(query.getRoleType()), AdminAccountEntity::getRoleType, query.getRoleType())
                .eq(Objects.nonNull(query.getStatus()), AdminAccountEntity::getStatus, query.getStatus())
                .eq(AdminAccountEntity::getDeleted, NOT_DELETED)
                .orderByDesc(AdminAccountEntity::getCreateDate)
                .last("LIMIT " + offset + "," + query.getSize())
                .list()
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(records, total, query.getPage(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAccountResponse createAccount(AdminAccountCreateRequest request) {
        if (Objects.nonNull(this.findByUsername(request.username().trim()))) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "登录账号已存在");
        }
        AdminAccountEntity account = new AdminAccountEntity();
        account.setUsername(request.username().trim());
        account.setPasswordHash(this.passwordEncoder.encode(request.password()));
        account.setDisplayName(request.displayName().trim());
        account.setRoleType(request.roleType());
        account.setStatus(request.status());
        account.setDeleted(NOT_DELETED);
        this.save(account);
        return this.toResponse(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAccountResponse updateAccount(Long id, AdminAccountUpdateRequest request, Long operatorId) {
        AdminAccountEntity account = this.requireAccount(id);
        if (Objects.equals(id, operatorId)
                && (!Objects.equals(ENABLED, request.status())
                || !Objects.equals(account.getRoleType(), request.roleType()))) {
            throw new BusinessException(HttpStatus.CONFLICT, 40902, "不能禁用当前账号或修改自己的角色");
        }
        account.setDisplayName(request.displayName().trim());
        account.setRoleType(request.roleType());
        account.setStatus(request.status());
        this.updateById(account);
        if (!Objects.equals(ENABLED, request.status())) {
            this.businessSessionService.revokeAdminSessions(id, LocalDateTime.now());
        }
        return this.toResponse(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String password) {
        this.requireAccount(id);
        this.lambdaUpdate()
                .set(AdminAccountEntity::getPasswordHash, this.passwordEncoder.encode(password))
                .eq(AdminAccountEntity::getId, id)
                .eq(AdminAccountEntity::getDeleted, NOT_DELETED)
                .update();
        this.businessSessionService.revokeAdminSessions(id, LocalDateTime.now());
    }

    private AdminAccountEntity requireAccount(Long id) {
        AdminAccountEntity account = this.getById(id);
        if (Objects.isNull(account)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40401, "后台账号不存在");
        }
        return account;
    }

    private AdminAccountResponse toResponse(AdminAccountEntity account) {
        return new AdminAccountResponse(account.getId(), account.getUsername(), account.getDisplayName(),
                account.getRoleType(), account.getStatus(), account.getLastLoginAt(), account.getCreateDate());
    }
}
