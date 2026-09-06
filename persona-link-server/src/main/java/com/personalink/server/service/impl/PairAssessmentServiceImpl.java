package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.CreatePairRequest;
import com.personalink.server.dto.JoinPairRequest;
import com.personalink.server.dto.PairCreateResponse;
import com.personalink.server.dto.PairReportResponse;
import com.personalink.server.dto.PairSessionResponse;
import com.personalink.server.entity.AnswerSessionEntity;
import com.personalink.server.entity.PairReportEntity;
import com.personalink.server.entity.PairSessionEntity;
import com.personalink.server.entity.TestEntity;
import com.personalink.server.entity.TestVersionEntity;
import com.personalink.server.enums.AnswerStatus;
import com.personalink.server.enums.PairStatus;
import com.personalink.server.enums.TestType;
import com.personalink.server.mapper.AnswerSessionMapper;
import com.personalink.server.mapper.AnswerSessionQuestionMapper;
import com.personalink.server.mapper.PairReportMapper;
import com.personalink.server.mapper.PairSessionMapper;
import com.personalink.server.mapper.TestMapper;
import com.personalink.server.mapper.TestVersionMapper;
import com.personalink.server.service.PairAssessmentService;
import com.personalink.server.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 双人邀请、加入、状态和报告业务实现。
 */
@Service
public class PairAssessmentServiceImpl extends ServiceImpl<PairSessionMapper, PairSessionEntity>
        implements PairAssessmentService {

    private static final int NORMAL = 0;
    private static final int VISIBLE = 1;
    private static final int INVITE_VALID_HOURS = 24;
    private static final String INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final AnswerSessionMapper answerSessionMapper;
    private final AnswerSessionQuestionMapper answerSessionQuestionMapper;
    private final TestVersionMapper testVersionMapper;
    private final TestMapper testMapper;
    private final PairReportMapper pairReportMapper;
    private final ObjectMapper objectMapper;

    public PairAssessmentServiceImpl(
            AnswerSessionMapper answerSessionMapper,
            AnswerSessionQuestionMapper answerSessionQuestionMapper,
            TestVersionMapper testVersionMapper,
            TestMapper testMapper,
            PairReportMapper pairReportMapper,
            ObjectMapper objectMapper) {
        this.answerSessionMapper = answerSessionMapper;
        this.answerSessionQuestionMapper = answerSessionQuestionMapper;
        this.testVersionMapper = testVersionMapper;
        this.testMapper = testMapper;
        this.pairReportMapper = pairReportMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PairCreateResponse create(String openId, CreatePairRequest request) {
        Long answerSessionId = this.parseId(request.answerSessionId());
        AnswerSessionEntity answerSession = this.answerSessionMapper.selectOne(
                Wrappers.<AnswerSessionEntity>lambdaQuery()
                        .eq(AnswerSessionEntity::getId, answerSessionId)
                        .eq(AnswerSessionEntity::getDeleted, NORMAL)
                        .last("FOR UPDATE"), false);
        if (Objects.isNull(answerSession) || !Objects.equals(answerSession.getOpenId(), openId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.value(), "答卷不存在");
        }
        if (AnswerStatus.REPORT_READY.getCode() != answerSession.getAnswerStatus()) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "完成双人答卷后才能创建邀请");
        }
        TestVersionEntity version = this.testVersionMapper.selectById(answerSession.getVersionId());
        TestEntity test = Objects.isNull(version) ? null : this.testMapper.selectById(version.getTestId());
        if (Objects.isNull(test) || TestType.PAIR.getCode() != test.getTestType()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "当前答卷不是双人评测");
        }
        String inviteToken = this.generateInviteToken(openId, request.createRequestId());
        PairSessionEntity retried = this.getOne(Wrappers.<PairSessionEntity>lambdaQuery()
                .eq(PairSessionEntity::getCreateRequestId, request.createRequestId())
                .eq(PairSessionEntity::getDeleted, NORMAL), false);
        if (Objects.nonNull(retried)) {
            if (!Objects.equals(retried.getInitiatorOpenId(), openId)
                    || !Objects.equals(retried.getInitiatorAnswerSessionId(), answerSessionId)
                    || !Objects.equals(retried.getInviteTokenHash(), this.hashToken(inviteToken))) {
                throw new BusinessException(HttpStatus.CONFLICT, HttpStatus.CONFLICT.value(), "创建请求号已使用");
            }
            return new PairCreateResponse(this.toResponse(retried, openId), inviteToken);
        }
        PairSessionEntity active = this.lambdaQuery()
                .eq(PairSessionEntity::getInitiatorAnswerSessionId, answerSession.getId())
                .notIn(PairSessionEntity::getPairStatus,
                        PairStatus.CANCELLED.getCode(), PairStatus.EXPIRED.getCode())
                .eq(PairSessionEntity::getDeleted, NORMAL)
                .orderByDesc(PairSessionEntity::getId)
                .last("LIMIT 1")
                .one();
        if (Objects.nonNull(active)
                && PairStatus.INITIATOR_DONE.getCode() == active.getPairStatus()
                && active.getExpiresAt().isBefore(LocalDateTime.now())) {
            this.lambdaUpdate()
                    .set(PairSessionEntity::getPairStatus, PairStatus.EXPIRED.getCode())
                    .eq(PairSessionEntity::getId, active.getId())
                    .eq(PairSessionEntity::getPairStatus, PairStatus.INITIATOR_DONE.getCode())
                    .eq(PairSessionEntity::getDeleted, NORMAL)
                    .update();
            active = null;
        }
        if (Objects.nonNull(active)) {
            throw new BusinessException(HttpStatus.CONFLICT, HttpStatus.CONFLICT.value(),
                    "该答卷已有有效邀请，请先取消原邀请");
        }
        PairSessionEntity pair = new PairSessionEntity();
        pair.setPairNo(this.businessNo());
        pair.setCreateRequestId(request.createRequestId());
        pair.setInviteTokenHash(this.hashToken(inviteToken));
        pair.setVersionId(answerSession.getVersionId());
        pair.setInitiatorOpenId(openId);
        pair.setInitiatorAnswerSessionId(answerSession.getId());
        pair.setPairStatus(PairStatus.INITIATOR_DONE.getCode());
        pair.setInitiatorVisibleFlag(VISIBLE);
        pair.setPartnerVisibleFlag(VISIBLE);
        pair.setExpiresAt(LocalDateTime.now().plusHours(INVITE_VALID_HOURS));
        pair.setDeleted(NORMAL);
        if (this.baseMapper.insertIgnore(pair) == 0) {
            PairSessionEntity concurrent = this.getOne(Wrappers.<PairSessionEntity>lambdaQuery()
                    .eq(PairSessionEntity::getCreateRequestId, request.createRequestId())
                    .eq(PairSessionEntity::getDeleted, NORMAL), false);
            if (Objects.nonNull(concurrent)
                    && Objects.equals(concurrent.getInitiatorOpenId(), openId)
                    && Objects.equals(concurrent.getInitiatorAnswerSessionId(), answerSessionId)
                    && Objects.equals(concurrent.getInviteTokenHash(), this.hashToken(inviteToken))) {
                return new PairCreateResponse(this.toResponse(concurrent, openId), inviteToken);
            }
            throw new BusinessException(HttpStatus.CONFLICT, HttpStatus.CONFLICT.value(), "邀请码冲突，请重试");
        }
        return new PairCreateResponse(this.toResponse(pair, openId), inviteToken);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PairSessionResponse join(String openId, JoinPairRequest request) {
        PairSessionEntity pair = this.lambdaQuery()
                .eq(PairSessionEntity::getInviteTokenHash,
                        this.hashToken(request.inviteToken().toUpperCase(Locale.ROOT)))
                .eq(PairSessionEntity::getDeleted, NORMAL)
                .last("FOR UPDATE")
                .one();
        if (Objects.isNull(pair)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.value(), "邀请不存在");
        }
        if (Objects.equals(pair.getPartnerOpenId(), openId)
                && Objects.nonNull(pair.getPartnerAnswerSessionId())) {
            return this.toResponse(pair, openId);
        }
        if (PairStatus.INITIATOR_DONE.getCode() != pair.getPairStatus()) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "邀请已失效或已被加入");
        }
        if (pair.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "邀请已过期");
        }
        if (Objects.equals(pair.getInitiatorOpenId(), openId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "不能加入自己创建的邀请");
        }
        AnswerSessionEntity requestOwner = this.answerSessionMapper.selectOne(
                Wrappers.<AnswerSessionEntity>lambdaQuery()
                        .eq(AnswerSessionEntity::getCreateRequestId, request.createRequestId())
                        .eq(AnswerSessionEntity::getDeleted, NORMAL), false);
        if (Objects.nonNull(requestOwner)) {
            throw new BusinessException(HttpStatus.CONFLICT, HttpStatus.CONFLICT.value(), "创建请求号已使用");
        }
        AnswerSessionEntity partnerSession = new AnswerSessionEntity();
        partnerSession.setAnswerNo(this.businessNo());
        partnerSession.setCreateRequestId(request.createRequestId());
        partnerSession.setOpenId(openId);
        partnerSession.setVersionId(pair.getVersionId());
        partnerSession.setAnswerType(TestType.PAIR.getCode());
        partnerSession.setAnswerStatus(AnswerStatus.IN_PROGRESS.getCode());
        partnerSession.setDeleted(NORMAL);
        if (this.answerSessionMapper.insertIgnore(partnerSession) == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, HttpStatus.CONFLICT.value(), "创建请求号已使用");
        }
        int copied = this.answerSessionQuestionMapper.copyFromSession(
                pair.getInitiatorAnswerSessionId(), partnerSession.getId());
        if (copied <= 0) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "发起者题目快照不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean updated = this.lambdaUpdate()
                .set(PairSessionEntity::getPartnerOpenId, openId)
                .set(PairSessionEntity::getPartnerAnswerSessionId, partnerSession.getId())
                .set(PairSessionEntity::getJoinedAt, now)
                .set(PairSessionEntity::getPairStatus, PairStatus.PARTNER_JOINED.getCode())
                .eq(PairSessionEntity::getId, pair.getId())
                .isNull(PairSessionEntity::getPartnerOpenId)
                .eq(PairSessionEntity::getPairStatus, PairStatus.INITIATOR_DONE.getCode())
                .eq(PairSessionEntity::getDeleted, NORMAL)
                .update();
        if (!updated) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "邀请已被加入");
        }
        pair.setPartnerOpenId(openId);
        pair.setPartnerAnswerSessionId(partnerSession.getId());
        pair.setJoinedAt(now);
        pair.setPairStatus(PairStatus.PARTNER_JOINED.getCode());
        return this.toResponse(pair, openId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PairSessionResponse getStatus(String openId, Long pairSessionId) {
        PairSessionEntity pair = this.requireOwnedPair(pairSessionId, openId);
        if (PairStatus.INITIATOR_DONE.getCode() == pair.getPairStatus()
                && pair.getExpiresAt().isBefore(LocalDateTime.now())) {
            this.lambdaUpdate()
                    .set(PairSessionEntity::getPairStatus, PairStatus.EXPIRED.getCode())
                    .eq(PairSessionEntity::getId, pair.getId())
                    .eq(PairSessionEntity::getPairStatus, PairStatus.INITIATOR_DONE.getCode())
                    .eq(PairSessionEntity::getDeleted, NORMAL)
                    .update();
            pair.setPairStatus(PairStatus.EXPIRED.getCode());
        }
        return this.toResponse(pair, openId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String openId, Long pairSessionId) {
        PairSessionEntity pair = this.lambdaQuery()
                .eq(PairSessionEntity::getId, pairSessionId)
                .eq(PairSessionEntity::getDeleted, NORMAL)
                .last("FOR UPDATE")
                .one();
        this.validateOwnership(pair, openId);
        if (PairStatus.INITIATOR_DONE.getCode() != pair.getPairStatus()
                && PairStatus.PARTNER_JOINED.getCode() != pair.getPairStatus()) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "当前配对状态不允许取消");
        }
        this.lambdaUpdate()
                .set(PairSessionEntity::getPairStatus, PairStatus.CANCELLED.getCode())
                .eq(PairSessionEntity::getId, pair.getId())
                .in(PairSessionEntity::getPairStatus,
                        PairStatus.INITIATOR_DONE.getCode(), PairStatus.PARTNER_JOINED.getCode())
                .eq(PairSessionEntity::getDeleted, NORMAL)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public PairReportResponse getReport(String openId, Long pairSessionId) {
        PairSessionEntity pair = this.requireOwnedPair(pairSessionId, openId);
        PairReportEntity report = this.pairReportMapper.selectOne(
                Wrappers.<PairReportEntity>lambdaQuery()
                        .eq(PairReportEntity::getPairSessionId, pair.getId())
                        .eq(PairReportEntity::getDeleted, NORMAL), false);
        if (Objects.isNull(report)) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "双人报告尚未生成");
        }
        return new PairReportResponse(
                String.valueOf(report.getId()),
                String.valueOf(pair.getId()),
                this.readJson(report.getResultSnapshot()),
                report.getGeneratedAt());
    }

    private PairSessionEntity requireOwnedPair(Long pairSessionId, String openId) {
        PairSessionEntity pair = this.getById(pairSessionId);
        this.validateOwnership(pair, openId);
        return pair;
    }

    private void validateOwnership(PairSessionEntity pair, String openId) {
        if (Objects.isNull(pair)
                || (!Objects.equals(pair.getInitiatorOpenId(), openId)
                && !Objects.equals(pair.getPartnerOpenId(), openId))) {
            throw new BusinessException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.value(), "配对记录不存在");
        }
    }

    private PairSessionResponse toResponse(PairSessionEntity pair, String openId) {
        PairReportEntity report = this.pairReportMapper.selectOne(
                Wrappers.<PairReportEntity>lambdaQuery()
                        .eq(PairReportEntity::getPairSessionId, pair.getId())
                        .eq(PairReportEntity::getDeleted, NORMAL), false);
        int status = pair.getPairStatus();
        if (PairStatus.INITIATOR_DONE.getCode() == status
                && pair.getExpiresAt().isBefore(LocalDateTime.now())) {
            status = PairStatus.EXPIRED.getCode();
        }
        return new PairSessionResponse(
                String.valueOf(pair.getId()),
                status,
                Objects.equals(pair.getInitiatorOpenId(), openId) ? "INITIATOR" : "PARTNER",
                String.valueOf(pair.getInitiatorAnswerSessionId()),
                Objects.isNull(pair.getPartnerAnswerSessionId())
                        ? null : String.valueOf(pair.getPartnerAnswerSessionId()),
                Objects.isNull(report) ? null : String.valueOf(report.getId()),
                pair.getExpiresAt());
    }

    private String generateInviteToken(String openId, String createRequestId) {
        byte[] digest = this.sha256(openId + "\n" + createRequestId);
        StringBuilder token = new StringBuilder(5);
        for (int index = 0; index < 5; index++) {
            token.append(INVITE_ALPHABET.charAt(Byte.toUnsignedInt(digest[index]) % INVITE_ALPHABET.length()));
        }
        return token.toString();
    }

    private String hashToken(String token) {
        return HexFormat.of().formatHex(this.sha256(token));
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR.value(), "邀请令牌生成失败");
        }
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "ID 格式错误");
        }
    }

    private JsonNode readJson(String value) {
        try {
            return this.objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "双人报告快照格式错误");
        }
    }

    private String businessNo() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
