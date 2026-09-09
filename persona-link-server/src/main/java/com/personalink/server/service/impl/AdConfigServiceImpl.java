package com.personalink.server.service.impl;

import lombok.RequiredArgsConstructor;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.*;
import com.personalink.server.entity.*;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.AdConfigMapper;
import com.personalink.server.mapper.ContentAuditLogMapper;
import com.personalink.server.service.AdConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;

/** 单行全局广告配置，不缓存，保存后下一次访问即使用新策略。 */
@Service
@RequiredArgsConstructor
public class AdConfigServiceImpl extends ServiceImpl<AdConfigMapper, AdConfigEntity> implements AdConfigService {
    private static final Logger LOG = LoggerFactory.getLogger(AdConfigServiceImpl.class);
    private final ContentAuditLogMapper auditMapper;
    private final ObjectMapper objectMapper;
    @Override
    public AdConfigEntity current() {
        return this.requireConfig(this.getById(1L));
    }

    @Override
    public AdConfigEntity lockCurrent() {
        // ponytail: 短事务以配置行串行化授权/失败额度；高吞吐时改为用户级额度锁。
        return this.requireConfig(this.lambdaQuery().eq(AdConfigEntity::getId, 1L)
                .eq(AdConfigEntity::getDeleted, 0).last("FOR UPDATE").one());
    }

    @Override
    public AdConfigResponse readConfig() { return this.response(this.current()); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdConfigResponse saveConfig(AdConfigSaveRequest request, AdminAccountEntity operator) {
        if (Objects.isNull(operator) || !Integer.valueOf(1).equals(operator.getRoleType())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 403, "只有管理员可以配置广告");
        }
        AdConfigEntity current = this.lockCurrent();
        String before = this.json(this.response(current));
        current.setEnabled(request.enabled());
        current.setAdUnitId(request.adUnitId());
        current.setFailurePolicy(request.failurePolicy());
        current.setUpdatedAt(LocalDateTime.now());
        current.setUpdatedByName(operator.getDisplayName());
        this.updateById(current);
        ContentAuditLogEntity audit = new ContentAuditLogEntity();
        audit.setBizType(4);
        audit.setBizId(1L);
        audit.setActionType(2);
        audit.setBeforeSnapshot(before);
        audit.setAfterSnapshot(this.json(this.response(current)));
        audit.setReason("更新微信激励视频广告配置");
        audit.setOperatorId(operator.getId());
        audit.setCreateDate(current.getUpdatedAt());
        this.auditMapper.insert(audit);
        return this.response(current);
    }

    private AdConfigEntity requireConfig(AdConfigEntity config) {
        if (Objects.isNull(config)) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 503, "广告配置未初始化，请执行广告数据库迁移");
        }
        return config;
    }

    private AdConfigResponse response(AdConfigEntity config) {
        return new AdConfigResponse(Boolean.TRUE.equals(config.getEnabled()), config.getAdUnitId(),
                config.getFailurePolicy(), config.getUpdatedAt(), config.getUpdatedByName());
    }

    private String json(Object value) {
        try { return this.objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) {
            LOG.error("[广告配置] 审计序列化失败", exception);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 500, "广告配置审计序列化失败");
        }
    }
}
