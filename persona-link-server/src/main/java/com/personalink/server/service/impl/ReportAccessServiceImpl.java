package com.personalink.server.service.impl;

import lombok.RequiredArgsConstructor;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.personalink.server.dto.*;
import com.personalink.server.enums.*;
import com.personalink.server.entity.*;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.*;
import com.personalink.server.service.AdConfigService;
import com.personalink.server.service.ReportAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** 所有报告输出的统一授权入口；二期会员权益仅在这里接入。 */
@Service
@RequiredArgsConstructor
public class ReportAccessServiceImpl extends ServiceImpl<ReportAccessMapper, ReportAccessEntity>
        implements ReportAccessService {
    private static final Logger LOG = LoggerFactory.getLogger(ReportAccessServiceImpl.class);
    private final AdConfigService configService;
    private final ReportAdTaskMapper taskMapper;
    private final ReportMapper reportMapper;
    private final AnswerSessionMapper answerMapper;
    private final PairSessionMapper pairMapper;
    private final PairReportMapper pairReportMapper;

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ReportAccessResponse access(String openId, int reportType, Long reportId) {
        this.requireOwner(openId, reportType, reportId);
        if (this.hasGrant(openId, reportType, reportId)) { return this.allowed(); }
        AdConfigEntity config = this.configService.lockCurrent();
        if (this.hasGrant(openId, reportType, reportId)) { return this.allowed(); }
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            this.grant(openId, reportType, reportId, ReportGrantSource.AD_DISABLED.getCode());
            return this.allowed();
        }
        LocalDateTime now = LocalDateTime.now();
        ReportAdTaskEntity task = this.taskMapper.selectOne(Wrappers.<ReportAdTaskEntity>lambdaQuery()
                .eq(ReportAdTaskEntity::getOpenId, openId)
                .eq(ReportAdTaskEntity::getReportType, reportType)
                .eq(ReportAdTaskEntity::getReportId, reportId)
                .eq(ReportAdTaskEntity::getAdUnitId, config.getAdUnitId())
                .isNull(ReportAdTaskEntity::getConsumedAt)
                .gt(ReportAdTaskEntity::getExpiresAt, now)
                .eq(ReportAdTaskEntity::getDeleted, 0)
                .orderByDesc(ReportAdTaskEntity::getId).last("LIMIT 1"), false);
        if (Objects.isNull(task)) {
            long count = this.taskMapper.selectCount(Wrappers.<ReportAdTaskEntity>lambdaQuery()
                    .eq(ReportAdTaskEntity::getOpenId, openId)
                    .ge(ReportAdTaskEntity::getCreateDate, now.minusHours(1))
                    .eq(ReportAdTaskEntity::getDeleted, 0));
            if (count >= 20) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 429, "广告请求过于频繁，请稍后再试");
            }
            task = new ReportAdTaskEntity();
            task.setOpenId(openId);
            task.setReportType(reportType);
            task.setReportId(reportId);
            task.setTaskId(UUID.randomUUID().toString());
            task.setAdUnitId(config.getAdUnitId());
            task.setCreateDate(now);
            task.setExpiresAt(now.plusMinutes(10));
            task.setDeleted(0);
            this.taskMapper.insert(task);
        }
        return new ReportAccessResponse(ReportAccessStatus.AD_REQUIRED.getCode(),
                task.getAdUnitId(), task.getTaskId(), task.getExpiresAt());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ReportAccessResponse submitAd(String openId, int reportType, Long reportId, AdResultRequest request) {
        this.requireOwner(openId, reportType, reportId);
        if (this.hasGrant(openId, reportType, reportId)) { return this.allowed(); }
        AdConfigEntity config = this.configService.lockCurrent();
        if (this.hasGrant(openId, reportType, reportId)) { return this.allowed(); }
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            this.grant(openId, reportType, reportId, ReportGrantSource.AD_DISABLED.getCode());
            return this.allowed();
        }
        ReportAdTaskEntity task = this.taskMapper.selectOne(Wrappers.<ReportAdTaskEntity>lambdaQuery()
                .eq(ReportAdTaskEntity::getTaskId, request.taskId())
                .eq(ReportAdTaskEntity::getOpenId, openId)
                .eq(ReportAdTaskEntity::getReportType, reportType)
                .eq(ReportAdTaskEntity::getReportId, reportId)
                .eq(ReportAdTaskEntity::getDeleted, 0), false);
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(task) || Objects.nonNull(task.getConsumedAt())
                || !task.getExpiresAt().isAfter(now)
                || !Objects.equals(task.getAdUnitId(), config.getAdUnitId())) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "广告任务已失效，请重新获取");
        }
        boolean completed = Objects.equals(AdOutcome.COMPLETED.getCode(), request.outcome());
        boolean free = false;
        if (!completed && Objects.equals(AdFailurePolicy.FREE.getCode(), config.getFailurePolicy())) {
            long count = this.lambdaQuery().eq(ReportAccessEntity::getOpenId, openId)
                    .eq(ReportAccessEntity::getGrantSource, ReportGrantSource.AD_FAILURE.getCode())
                    .ge(ReportAccessEntity::getGrantedAt, now.toLocalDate().atStartOfDay())
                    .eq(ReportAccessEntity::getDeleted, 0).count();
            free = count < 3;
            LOG.warn("[广告解锁] 加载失败，用户：{}，报告类型：{}，报告：{}，错误码：{}，免费放行：{}",
                    openId, reportType, reportId, request.errorCode(), free);
        }
        // 客户端回调不可证明真实观看。任务及每日限额仅降低伪造风险，不代替微信服务端凭证。
        task.setConsumedAt(now);
        task.setOutcome(request.outcome());
        task.setErrorCode(request.errorCode());
        this.taskMapper.updateById(task);
        if (completed || free) {
            this.grant(openId, reportType, reportId, completed
                    ? ReportGrantSource.AD_COMPLETED.getCode() : ReportGrantSource.AD_FAILURE.getCode());
            return this.allowed();
        }
        // 失败任务已消耗；下一次 access 获取新任务，不让失败事件重放成为完成事件。
        return new ReportAccessResponse(ReportAccessStatus.AD_REQUIRED.getCode(), null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean canRead(String openId, int reportType, Long reportId) {
        this.requireOwner(openId, reportType, reportId);
        if (this.hasGrant(openId, reportType, reportId)) { return true; }
        AdConfigEntity config = this.configService.lockCurrent();
        if (Boolean.TRUE.equals(config.getEnabled())) { return this.hasGrant(openId, reportType, reportId); }
        this.grant(openId, reportType, reportId, ReportGrantSource.AD_DISABLED.getCode());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void requireRead(String openId, int reportType, Long reportId) {
        if (!this.canRead(openId, reportType, reportId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "请观看广告解锁本次结果");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantFreeHistory(String openId) {
        // 以此次读取的开关决定本次免费授权；不持配置锁再批量读取答卷，避免和提交锁顺序相反。
        AdConfigEntity config = this.configService.current();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            // 与隐私删除使用同一批答卷行锁，避免清理授权后历史查询重新写入旧授权。
            this.answerMapper.selectList(Wrappers.<AnswerSessionEntity>lambdaQuery()
                    .eq(AnswerSessionEntity::getOpenId, openId).eq(AnswerSessionEntity::getDeleted, 0)
                    .orderByAsc(AnswerSessionEntity::getId).last("FOR UPDATE"));
            this.baseMapper.grantHistory(openId);
        }
    }

    private boolean hasGrant(String openId, int reportType, Long reportId) {
        return this.lambdaQuery().eq(ReportAccessEntity::getOpenId, openId)
                .eq(ReportAccessEntity::getReportType, reportType)
                .eq(ReportAccessEntity::getReportId, reportId)
                .eq(ReportAccessEntity::getDeleted, 0).exists();
    }

    private void grant(String openId, int reportType, Long reportId, int source) {
        ReportAccessEntity grant = new ReportAccessEntity();
        grant.setOpenId(openId);
        grant.setReportType(reportType);
        grant.setReportId(reportId);
        grant.setGrantSource(source);
        grant.setGrantedAt(LocalDateTime.now());
        this.baseMapper.insertIgnore(grant);
    }

    private void requireOwner(String openId, int reportType, Long reportId) {
        if (reportType == ReportKind.SINGLE.getCode()) {
            ReportEntity report = this.reportMapper.selectById(reportId);
            AnswerSessionEntity answer = Objects.isNull(report) ? null
                    : this.answerMapper.selectOne(Wrappers.<AnswerSessionEntity>lambdaQuery()
                            .eq(AnswerSessionEntity::getId, report.getAnswerSessionId())
                            .eq(AnswerSessionEntity::getDeleted, 0).last("FOR UPDATE"), false);
            if (Objects.isNull(answer) || !Objects.equals(openId, answer.getOpenId())) {
                throw new BusinessException(HttpStatus.NOT_FOUND, 404, "报告不存在");
            }
        } else if (reportType == ReportKind.PAIR.getCode()) {
            PairSessionEntity pair = this.pairMapper.selectOne(Wrappers.<PairSessionEntity>lambdaQuery()
                    .eq(PairSessionEntity::getId, reportId).eq(PairSessionEntity::getDeleted, 0)
                    .last("FOR UPDATE"), false);
            if (Objects.isNull(pair) || !pair.isVisibleTo(openId)) {
                throw new BusinessException(HttpStatus.NOT_FOUND, 404, "双人报告不存在");
            }
            if (this.pairReportMapper.selectCount(Wrappers.<PairReportEntity>lambdaQuery()
                    .eq(PairReportEntity::getPairSessionId, reportId)
                    .eq(PairReportEntity::getDeleted, 0)) == 0) {
                throw new BusinessException(HttpStatus.CONFLICT, 409, "双人报告尚未生成");
            }
        } else { throw new BusinessException(HttpStatus.BAD_REQUEST, 400, "报告类型错误"); }
    }

    private ReportAccessResponse allowed() {
        return new ReportAccessResponse(ReportAccessStatus.ALLOWED.getCode(), null, null, null);
    }
}
