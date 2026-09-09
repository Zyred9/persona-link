package com.personalink.server.service.impl;

import lombok.RequiredArgsConstructor;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalink.server.dto.*;
import com.personalink.server.entity.*;
import com.personalink.server.enums.AnswerStatus;
import com.personalink.server.enums.PairStatus;
import com.personalink.server.enums.QuestionType;
import com.personalink.server.enums.ReportKind;
import com.personalink.server.enums.TestType;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.*;
import com.personalink.server.model.ScoreAccumulator;
import com.personalink.server.service.AssessmentService;
import com.personalink.server.service.ReportAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 小程序答卷、计分和单人报告业务实现。
 */
@Service
@RequiredArgsConstructor
public class AssessmentServiceImpl extends ServiceImpl<AnswerSessionMapper, AnswerSessionEntity>
        implements AssessmentService {

    private static final int NORMAL = 0;
    private static final int ENABLED = 1;
    private static final int PUBLISHED = 4;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TestMapper testMapper;
    private final TestVersionMapper testVersionMapper;
    private final ScoreDimensionMapper scoreDimensionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final ResultTemplateMapper resultTemplateMapper;
    private final AnswerSessionQuestionMapper answerSessionQuestionMapper;
    private final AnswerDetailMapper answerDetailMapper;
    private final ReportMapper reportMapper;
    private final PairSessionMapper pairSessionMapper;
    private final PairReportMapper pairReportMapper;
    private final ObjectMapper objectMapper;
    private final ReportAccessService reportAccessService;

    @Override
    @Transactional(readOnly = true)
    public AssessmentSessionResponse current(String openId) {
        AnswerSessionEntity session = this.lambdaQuery()
                .eq(AnswerSessionEntity::getOpenId, openId)
                .eq(AnswerSessionEntity::getAnswerStatus, AnswerStatus.IN_PROGRESS.getCode())
                .eq(AnswerSessionEntity::getDeleted, NORMAL)
                .orderByDesc(AnswerSessionEntity::getCreateDate)
                .orderByDesc(AnswerSessionEntity::getId)
                .last("LIMIT 1")
                .one();
        return Objects.isNull(session) ? null : this.buildSessionResponse(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssessmentSessionResponse create(String openId, CreateAssessmentRequest request) {
        return this.createSession(openId, this.parseId(request.testId()), request.createRequestId());
    }

    @Override
    @Transactional(readOnly = true)
    public AssessmentSessionResponse resume(String openId, Long answerSessionId) {
        AnswerSessionEntity session = this.requireOwnedSession(answerSessionId, openId);
        return this.buildSessionResponse(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAnswer(
            String openId,
            Long answerSessionId,
            SaveAnswerRequest request) {
        AnswerSessionEntity session = this.requireOwnedLockedSession(answerSessionId, openId);
        this.requireInProgress(session);
        Long questionId = this.parseId(request.questionId());
        long snapshotCount = this.answerSessionQuestionMapper.selectCount(
                Wrappers.<AnswerSessionQuestionEntity>lambdaQuery()
                        .eq(AnswerSessionQuestionEntity::getAnswerSessionId, session.getId())
                        .eq(AnswerSessionQuestionEntity::getQuestionId, questionId)
                        .eq(AnswerSessionQuestionEntity::getDeleted, NORMAL));
        if (snapshotCount == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "题目不属于当前答卷");
        }
        QuestionEntity question = this.questionMapper.selectById(questionId);
        if (Objects.isNull(question)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "题目不存在");
        }
        List<Long> optionIds = request.optionIds().stream().map(this::parseId).toList();
        if (new HashSet<>(optionIds).size() != optionIds.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "选项不能重复");
        }
        this.validateSelectionCount(question, optionIds.size());
        List<QuestionOptionEntity> options = optionIds.isEmpty() ? List.of() : this.questionOptionMapper.selectList(
                Wrappers.<QuestionOptionEntity>lambdaQuery()
                        .eq(QuestionOptionEntity::getQuestionId, questionId)
                        .in(QuestionOptionEntity::getId, optionIds)
                        .eq(QuestionOptionEntity::getDeleted, NORMAL));
        if (options.size() != optionIds.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "选项不属于当前题目");
        }
        this.answerDetailMapper.update(null,
                Wrappers.<AnswerDetailEntity>lambdaUpdate()
                        .set(AnswerDetailEntity::getDeleted, 1)
                        .eq(AnswerDetailEntity::getAnswerSessionId, session.getId())
                        .eq(AnswerDetailEntity::getQuestionId, questionId)
                        .eq(AnswerDetailEntity::getDeleted, NORMAL));
        List<AnswerDetailEntity> details = optionIds.stream().map(optionId -> {
            AnswerDetailEntity detail = new AnswerDetailEntity();
            detail.setAnswerSessionId(session.getId());
            detail.setQuestionId(questionId);
            detail.setOptionId(optionId);
            return detail;
        }).toList();
        if (!details.isEmpty()) {
            this.answerDetailMapper.upsertBatch(details);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssessmentSessionResponse restart(
            String openId,
            Long answerSessionId,
            RestartAssessmentRequest request) {
        AnswerSessionEntity session = this.requireOwnedLockedSession(answerSessionId, openId);
        if (Objects.nonNull(this.findPartnerPair(session))) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "受邀答卷不能重新开始，请继续完成当前配对");
        }
        TestVersionEntity version = this.requireVersion(session.getVersionId());
        AnswerSessionEntity retried = this.baseMapper.selectOne(
                Wrappers.<AnswerSessionEntity>lambdaQuery()
                        .eq(AnswerSessionEntity::getCreateRequestId, request.createRequestId())
                        .eq(AnswerSessionEntity::getDeleted, NORMAL), false);
        if (Objects.nonNull(retried)) {
            this.validateCreateRetry(retried, openId, version.getTestId());
            return this.buildSessionResponse(retried);
        }
        this.requireInProgress(session);
        this.lambdaUpdate()
                .set(AnswerSessionEntity::getAnswerStatus, AnswerStatus.ABANDONED.getCode())
                .eq(AnswerSessionEntity::getId, session.getId())
                .eq(AnswerSessionEntity::getAnswerStatus, AnswerStatus.IN_PROGRESS.getCode())
                .eq(AnswerSessionEntity::getDeleted, NORMAL)
                .update();
        return this.createSession(openId, version.getTestId(), request.createRequestId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportResponse submit(
            String openId,
            Long answerSessionId,
            SubmitAssessmentRequest request) {
        AnswerSessionEntity session = this.requireOwnedLockedSession(answerSessionId, openId);
        ReportEntity existingReport = this.findReportBySessionId(session.getId());
        if (Objects.nonNull(existingReport)) {
            return this.toSubmissionResponse(openId, existingReport);
        }
        this.requireInProgress(session);
        AnswerSessionEntity requestOwner = this.baseMapper.selectOne(
                Wrappers.<AnswerSessionEntity>lambdaQuery()
                        .eq(AnswerSessionEntity::getSubmitRequestId, request.submitRequestId())
                        .eq(AnswerSessionEntity::getDeleted, NORMAL), false);
        if (Objects.nonNull(requestOwner) && !Objects.equals(requestOwner.getId(), session.getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, HttpStatus.CONFLICT.value(), "提交请求号已使用");
        }

        List<AnswerSessionQuestionEntity> snapshots = this.listSnapshots(session.getId());
        List<Long> questionIds = snapshots.stream()
                .map(AnswerSessionQuestionEntity::getQuestionId)
                .toList();
        List<QuestionEntity> questions = this.listQuestions(questionIds);
        List<QuestionOptionEntity> options = this.listOptions(questionIds);
        List<AnswerDetailEntity> details = this.listAnswerDetails(session.getId());
        this.validateSubmission(snapshots, questions, options, details);

        List<ScoreDimensionEntity> dimensions = this.listDimensions(session.getVersionId());
        List<DimensionScoreResponse> scores = this.calculateScores(dimensions, questions, options, details);
        DimensionScoreResponse mainDimension = scores.stream()
                .max(Comparator.comparing(DimensionScoreResponse::normalizedScore)
                        .thenComparing(score -> -this.dimensionSort(dimensions, score.dimensionId())))
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT.value(), "题型未配置计分维度"));
        ResultTemplateEntity template = this.matchTemplate(
                session.getVersionId(), this.parseId(mainDimension.dimensionId()), mainDimension.normalizedScore());
        ReportSnapshotResponse snapshot = new ReportSnapshotResponse(
                template.getResultCode(),
                template.getResultName(),
                this.readJson(template.getBasicResultJson()),
                this.readJson(template.getDeepResultJson()),
                this.readJson(template.getShareCopyJson()),
                scores);
        LocalDateTime now = LocalDateTime.now();
        ReportEntity report = new ReportEntity();
        report.setReportNo(this.businessNo());
        report.setAnswerSessionId(session.getId());
        report.setResultCode(template.getResultCode());
        report.setResultSnapshot(this.writeJson(snapshot));
        report.setGeneratedAt(now);
        this.reportMapper.insertIgnore(report);
        ReportEntity savedReport = this.findReportBySessionId(session.getId());
        if (Objects.isNull(savedReport)) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "报告生成失败");
        }
        this.lambdaUpdate()
                .set(AnswerSessionEntity::getSubmitRequestId, request.submitRequestId())
                .set(AnswerSessionEntity::getSubmittedAt, now)
                .set(AnswerSessionEntity::getReportReadyAt, now)
                .set(AnswerSessionEntity::getAnswerStatus, AnswerStatus.REPORT_READY.getCode())
                .eq(AnswerSessionEntity::getId, session.getId())
                .eq(AnswerSessionEntity::getAnswerStatus, AnswerStatus.IN_PROGRESS.getCode())
                .eq(AnswerSessionEntity::getDeleted, NORMAL)
                .update();
        this.tryGeneratePairReport(session.getId());
        return this.toSubmissionResponse(openId, savedReport);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportResponse getReport(String openId, Long reportId) {
        ReportEntity report = this.reportMapper.selectById(reportId);
        if (Objects.isNull(report)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.value(), "报告不存在");
        }
        this.requireOwnedSession(report.getAnswerSessionId(), openId);
        this.reportAccessService.requireRead(openId, ReportKind.SINGLE.getCode(), reportId);
        return this.toReportResponse(report);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageResponse<ReportHistoryResponse> history(String openId, long page, long size) {
        this.reportAccessService.grantFreeHistory(openId);
        long total = this.reportMapper.countHistory(openId);
        List<ReportHistoryResponse> records = this.reportMapper
                .selectHistory(openId, (page - 1) * size, size)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
        return new PageResponse<>(records, total, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReport(String openId, Long reportId) {
        ReportEntity report = this.reportMapper.selectById(reportId);
        if (Objects.isNull(report)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.value(), "报告不存在");
        }
        this.requireOwnedSession(report.getAnswerSessionId(), openId);
        this.reportMapper.deleteById(reportId);
        this.pairSessionMapper.update(null,
                Wrappers.<PairSessionEntity>lambdaUpdate()
                        .set(PairSessionEntity::getPairStatus, PairStatus.CANCELLED.getCode())
                        .and(wrapper -> wrapper
                                .eq(PairSessionEntity::getInitiatorAnswerSessionId, report.getAnswerSessionId())
                                .or()
                                .eq(PairSessionEntity::getPartnerAnswerSessionId, report.getAnswerSessionId()))
                        .in(PairSessionEntity::getPairStatus,
                                PairStatus.INITIATOR_DONE.getCode(),
                                PairStatus.PARTNER_JOINED.getCode(),
                                PairStatus.BOTH_DONE.getCode())
                        .eq(PairSessionEntity::getDeleted, NORMAL));
    }

    private AssessmentSessionResponse createSession(String openId, Long testId, String createRequestId) {
        AnswerSessionEntity existing = this.baseMapper.selectOne(
                Wrappers.<AnswerSessionEntity>lambdaQuery()
                        .eq(AnswerSessionEntity::getCreateRequestId, createRequestId)
                        .eq(AnswerSessionEntity::getDeleted, NORMAL), false);
        if (Objects.nonNull(existing)) {
            this.validateCreateRetry(existing, openId, testId);
            return this.buildSessionResponse(existing);
        }
        TestEntity test = this.testMapper.selectOne(
                Wrappers.<TestEntity>lambdaQuery()
                        .eq(TestEntity::getId, testId)
                        .eq(TestEntity::getStatus, ENABLED)
                        .eq(TestEntity::getDeleted, NORMAL), false);
        if (Objects.isNull(test)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.value(), "评测不存在或已停用");
        }
        TestVersionEntity version = this.testVersionMapper.selectOne(
                Wrappers.<TestVersionEntity>lambdaQuery()
                        .eq(TestVersionEntity::getTestId, testId)
                        .eq(TestVersionEntity::getVersionStatus, PUBLISHED)
                        .eq(TestVersionEntity::getDeleted, NORMAL)
                        .orderByDesc(TestVersionEntity::getId)
                        .last("LIMIT 1"), false);
        if (Objects.isNull(version)) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "评测暂无已发布版本");
        }
        List<ScoreDimensionEntity> dimensions = this.listDimensions(version.getId());
        List<QuestionEntity> questions = this.questionMapper.selectList(
                Wrappers.<QuestionEntity>lambdaQuery()
                        .eq(QuestionEntity::getVersionId, version.getId())
                        .eq(QuestionEntity::getDeleted, NORMAL)
                        .orderByAsc(QuestionEntity::getQuestionNo)
                        .orderByAsc(QuestionEntity::getId));
        List<Long> allQuestionIds = questions.stream().map(QuestionEntity::getId).toList();
        List<QuestionOptionEntity> options = this.listOptions(allQuestionIds);
        List<Long> selectedQuestionIds = this.drawQuestionIds(
                version.getDrawQuestionCount(), dimensions, questions, options);

        AnswerSessionEntity session = new AnswerSessionEntity();
        session.setAnswerNo(this.businessNo());
        session.setCreateRequestId(createRequestId);
        session.setOpenId(openId);
        session.setVersionId(version.getId());
        session.setAnswerType(test.getTestType());
        session.setAnswerStatus(AnswerStatus.IN_PROGRESS.getCode());
        session.setDeleted(NORMAL);
        if (this.baseMapper.insertIgnore(session) == 0) {
            AnswerSessionEntity concurrent = this.lambdaQuery()
                    .eq(AnswerSessionEntity::getCreateRequestId, createRequestId)
                    .eq(AnswerSessionEntity::getDeleted, NORMAL)
                    .last("FOR UPDATE")
                    .one();
            if (Objects.isNull(concurrent)) {
                throw new BusinessException(HttpStatus.CONFLICT.value(), "答卷创建冲突，请重试");
            }
            this.validateCreateRetry(concurrent, openId, testId);
            return this.buildSessionResponse(concurrent);
        }
        List<AnswerSessionQuestionEntity> snapshots = new ArrayList<>(selectedQuestionIds.size());
        for (int index = 0; index < selectedQuestionIds.size(); index++) {
            AnswerSessionQuestionEntity snapshot = new AnswerSessionQuestionEntity();
            snapshot.setAnswerSessionId(session.getId());
            snapshot.setQuestionId(selectedQuestionIds.get(index));
            snapshot.setSortNo(index + 1);
            snapshots.add(snapshot);
        }
        this.answerSessionQuestionMapper.insertBatch(snapshots);
        return this.buildSessionResponse(session);
    }

    private void validateCreateRetry(AnswerSessionEntity session, String openId, Long testId) {
        TestVersionEntity version = this.requireVersion(session.getVersionId());
        if (!Objects.equals(session.getOpenId(), openId) || !Objects.equals(version.getTestId(), testId)) {
            throw new BusinessException(HttpStatus.CONFLICT, HttpStatus.CONFLICT.value(), "创建请求号已使用");
        }
    }

    private List<Long> drawQuestionIds(
            Integer drawCount,
            List<ScoreDimensionEntity> dimensions,
            List<QuestionEntity> questions,
            List<QuestionOptionEntity> options) {
        if (Objects.isNull(drawCount) || drawCount <= 0 || drawCount > questions.size()) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "已发布版本的抽题数量配置无效");
        }
        Map<Long, List<QuestionOptionEntity>> optionMap = options.stream()
                .collect(Collectors.groupingBy(QuestionOptionEntity::getQuestionId));
        Map<Long, Set<Long>> coverage = new HashMap<>();
        for (QuestionEntity question : questions) {
            Set<Long> dimensionIds = new HashSet<>();
            if (QuestionType.SINGLE.getCode() == question.getQuestionType()) {
                if (Objects.nonNull(question.getDimensionId())) {
                    dimensionIds.add(question.getDimensionId());
                }
            } else {
                optionMap.getOrDefault(question.getId(), Collections.emptyList()).stream()
                        .map(QuestionOptionEntity::getDimensionId)
                        .filter(Objects::nonNull)
                        .forEach(dimensionIds::add);
            }
            coverage.put(question.getId(), dimensionIds);
        }
        List<QuestionEntity> shuffled = new ArrayList<>(questions);
        Collections.shuffle(shuffled);
        LinkedHashSet<Long> selected = new LinkedHashSet<>();
        for (ScoreDimensionEntity dimension : dimensions) {
            boolean alreadyCovered = selected.stream()
                    .anyMatch(questionId -> coverage.getOrDefault(questionId, Collections.emptySet())
                            .contains(dimension.getId()));
            if (alreadyCovered) {
                continue;
            }
            QuestionEntity coveringQuestion = shuffled.stream()
                    .filter(question -> !selected.contains(question.getId()))
                    .filter(question -> coverage.getOrDefault(question.getId(), Collections.emptySet())
                            .contains(dimension.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT.value(), "已发布版本缺少维度覆盖题目"));
            selected.add(coveringQuestion.getId());
        }
        if (selected.size() > drawCount) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "抽题数量不足以覆盖全部维度");
        }
        for (QuestionEntity question : shuffled) {
            if (selected.size() >= drawCount) {
                break;
            }
            selected.add(question.getId());
        }
        return questions.stream()
                .map(QuestionEntity::getId)
                .filter(selected::contains)
                .toList();
    }

    private AssessmentSessionResponse buildSessionResponse(AnswerSessionEntity session) {
        TestVersionEntity version = this.requireVersion(session.getVersionId());
        List<AnswerSessionQuestionEntity> snapshots = this.listSnapshots(session.getId());
        List<Long> questionIds = snapshots.stream().map(AnswerSessionQuestionEntity::getQuestionId).toList();
        List<QuestionEntity> questions = this.listQuestions(questionIds);
        Map<Long, QuestionEntity> questionMap = questions.stream()
                .collect(Collectors.toMap(QuestionEntity::getId, Function.identity(), (first, second) -> first));
        Map<Long, List<QuestionOptionEntity>> optionMap = this.listOptions(questionIds).stream()
                .collect(Collectors.groupingBy(QuestionOptionEntity::getQuestionId));
        Map<Long, Set<Long>> selectedMap = this.listAnswerDetails(session.getId()).stream()
                .collect(Collectors.groupingBy(
                        AnswerDetailEntity::getQuestionId,
                        Collectors.mapping(AnswerDetailEntity::getOptionId, Collectors.toCollection(LinkedHashSet::new))));
        List<AssessmentQuestionResponse> responses = new ArrayList<>(snapshots.size());
        int firstUnansweredIndex = snapshots.size();
        for (int index = 0; index < snapshots.size(); index++) {
            AnswerSessionQuestionEntity snapshot = snapshots.get(index);
            QuestionEntity question = questionMap.get(snapshot.getQuestionId());
            if (Objects.isNull(question)) {
                throw new BusinessException(HttpStatus.CONFLICT.value(), "答卷题目快照无效");
            }
            Set<Long> selectedIds = selectedMap.getOrDefault(question.getId(), Collections.emptySet());
            if (selectedIds.isEmpty() && firstUnansweredIndex == snapshots.size()) {
                firstUnansweredIndex = index;
            }
            List<QuestionOptionEntity> questionOptions = optionMap.getOrDefault(
                    question.getId(), Collections.emptyList());
            List<AssessmentQuestionOptionResponse> optionResponses = questionOptions.stream()
                    .map(option -> new AssessmentQuestionOptionResponse(
                            String.valueOf(option.getId()), option.getOptionCode(), option.getOptionText()))
                    .toList();
            List<String> selectedOptionIds = questionOptions.stream()
                    .map(QuestionOptionEntity::getId)
                    .filter(selectedIds::contains)
                    .map(String::valueOf)
                    .toList();
            responses.add(new AssessmentQuestionResponse(
                    String.valueOf(question.getId()),
                    question.getQuestionType(),
                    question.getQuestionText(),
                    ENABLED == question.getRequiredFlag(),
                    question.getMinSelectCount(),
                    question.getMaxSelectCount(),
                    optionResponses,
                    selectedOptionIds));
        }
        PairSessionEntity partnerPair = this.findPartnerPair(session);
        return new AssessmentSessionResponse(
                String.valueOf(session.getId()),
                String.valueOf(version.getTestId()),
                String.valueOf(version.getId()),
                version.getTitle(),
                session.getAnswerStatus(),
                snapshots.size(),
                selectedMap.size(),
                firstUnansweredIndex,
                responses,
                session.getAnswerType(),
                Objects.isNull(partnerPair) ? null : String.valueOf(partnerPair.getId()),
                Objects.isNull(partnerPair) && AnswerStatus.IN_PROGRESS.getCode() == session.getAnswerStatus());
    }

    private PairSessionEntity findPartnerPair(AnswerSessionEntity session) {
        if (!Objects.equals(TestType.PAIR.getCode(), session.getAnswerType())) {
            return null;
        }
        // 受邀答卷必须始终沿用发起者的版本和题目快照，不能走普通重开流程。
        return this.pairSessionMapper.selectOne(Wrappers.<PairSessionEntity>lambdaQuery()
                .eq(PairSessionEntity::getPartnerAnswerSessionId, session.getId())
                .eq(PairSessionEntity::getPartnerOpenId, session.getOpenId())
                .eq(PairSessionEntity::getDeleted, NORMAL)
                .orderByDesc(PairSessionEntity::getId)
                .last("LIMIT 1"), false);
    }

    private void validateSubmission(
            List<AnswerSessionQuestionEntity> snapshots,
            List<QuestionEntity> questions,
            List<QuestionOptionEntity> options,
            List<AnswerDetailEntity> details) {
        Set<Long> snapshotQuestionIds = snapshots.stream()
                .map(AnswerSessionQuestionEntity::getQuestionId)
                .collect(Collectors.toSet());
        if (questions.size() != snapshotQuestionIds.size()) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "答卷题目快照无效");
        }
        Map<Long, List<AnswerDetailEntity>> detailMap = details.stream()
                .collect(Collectors.groupingBy(AnswerDetailEntity::getQuestionId));
        Map<Long, Set<Long>> validOptionMap = options.stream()
                .collect(Collectors.groupingBy(
                        QuestionOptionEntity::getQuestionId,
                        Collectors.mapping(QuestionOptionEntity::getId, Collectors.toSet())));
        for (QuestionEntity question : questions) {
            List<AnswerDetailEntity> selected = detailMap.getOrDefault(question.getId(), Collections.emptyList());
            if (ENABLED == question.getRequiredFlag() && selected.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "必答题尚未完成");
            }
            if (selected.isEmpty()) {
                continue;
            }
            this.validateSelectionCount(question, selected.size());
            Set<Long> validOptionIds = validOptionMap.getOrDefault(question.getId(), Collections.emptySet());
            if (selected.stream().map(AnswerDetailEntity::getOptionId).anyMatch(id -> !validOptionIds.contains(id))) {
                throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "答卷包含无效选项");
            }
        }
    }

    private List<DimensionScoreResponse> calculateScores(
            List<ScoreDimensionEntity> dimensions,
            List<QuestionEntity> questions,
            List<QuestionOptionEntity> options,
            List<AnswerDetailEntity> details) {
        Map<Long, ScoreAccumulator> accumulatorMap = dimensions.stream()
                .collect(Collectors.toMap(
                        ScoreDimensionEntity::getId,
                        dimension -> new ScoreAccumulator(),
                        (first, second) -> first,
                        LinkedHashMap::new));
        Map<Long, List<QuestionOptionEntity>> optionMap = options.stream()
                .collect(Collectors.groupingBy(QuestionOptionEntity::getQuestionId));
        Map<Long, Set<Long>> selectedMap = details.stream()
                .collect(Collectors.groupingBy(
                        AnswerDetailEntity::getQuestionId,
                        Collectors.mapping(AnswerDetailEntity::getOptionId, Collectors.toSet())));
        for (QuestionEntity question : questions) {
            List<QuestionOptionEntity> questionOptions = optionMap.getOrDefault(
                    question.getId(), Collections.emptyList());
            Set<Long> selectedIds = selectedMap.getOrDefault(question.getId(), Collections.emptySet());
            if (QuestionType.SINGLE.getCode() == question.getQuestionType()) {
                ScoreAccumulator accumulator = accumulatorMap.get(question.getDimensionId());
                if (Objects.isNull(accumulator) || questionOptions.isEmpty()) {
                    throw new BusinessException(HttpStatus.CONFLICT.value(), "单选题计分配置无效");
                }
                List<BigDecimal> values = questionOptions.stream()
                        .map(this::scoreValue)
                        .toList();
                BigDecimal minimum = Collections.min(values);
                BigDecimal maximum = Collections.max(values);
                if (ENABLED != question.getRequiredFlag()) {
                    minimum = minimum.min(BigDecimal.ZERO);
                    maximum = maximum.max(BigDecimal.ZERO);
                }
                accumulator.addMinimum(minimum);
                accumulator.addMaximum(maximum);
                questionOptions.stream()
                        .filter(option -> selectedIds.contains(option.getId()))
                        .map(this::scoreValue)
                        .forEach(accumulator::addRaw);
                continue;
            }
            for (ScoreDimensionEntity dimension : dimensions) {
                ScoreAccumulator accumulator = accumulatorMap.get(dimension.getId());
                List<BigDecimal> contributions = questionOptions.stream()
                        .map(option -> Objects.equals(option.getDimensionId(), dimension.getId())
                                ? this.scoreValue(option) : BigDecimal.ZERO)
                        .toList();
                accumulator.addMinimum(this.extremeSelection(
                        contributions,
                        ENABLED == question.getRequiredFlag() ? question.getMinSelectCount() : 0,
                        question.getMaxSelectCount(), false));
                accumulator.addMaximum(this.extremeSelection(
                        contributions,
                        ENABLED == question.getRequiredFlag() ? question.getMinSelectCount() : 0,
                        question.getMaxSelectCount(), true));
                questionOptions.stream()
                        .filter(option -> Objects.equals(option.getDimensionId(), dimension.getId()))
                        .filter(option -> selectedIds.contains(option.getId()))
                        .map(this::scoreValue)
                        .forEach(accumulator::addRaw);
            }
        }
        return dimensions.stream().map(dimension -> {
            ScoreAccumulator accumulator = accumulatorMap.get(dimension.getId());
            BigDecimal normalized = BigDecimal.ZERO;
            BigDecimal range = accumulator.getMaximum().subtract(accumulator.getMinimum());
            if (range.compareTo(BigDecimal.ZERO) > 0) {
                normalized = accumulator.getRaw()
                        .subtract(accumulator.getMinimum())
                        .multiply(HUNDRED)
                        .divide(range, 2, RoundingMode.HALF_UP)
                        .max(BigDecimal.ZERO)
                        .min(HUNDRED);
            }
            return new DimensionScoreResponse(
                    String.valueOf(dimension.getId()),
                    dimension.getDimensionCode(),
                    dimension.getDimensionName(),
                    accumulator.getRaw(),
                    accumulator.getMinimum(),
                    accumulator.getMaximum(),
                    normalized);
        }).toList();
    }

    private BigDecimal extremeSelection(
            List<BigDecimal> contributions,
            int minimumCount,
            int maximumCount,
            boolean maximum) {
        if (minimumCount < 0 || maximumCount < minimumCount || maximumCount > contributions.size()) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "多选题计分配置无效");
        }
        List<BigDecimal> sorted = new ArrayList<>(contributions);
        sorted.sort(maximum ? Comparator.reverseOrder() : Comparator.naturalOrder());
        BigDecimal result = null;
        for (int count = minimumCount; count <= maximumCount; count++) {
            BigDecimal sum = sorted.subList(0, count).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (Objects.isNull(result)
                    || (maximum && sum.compareTo(result) > 0)
                    || (!maximum && sum.compareTo(result) < 0)) {
                result = sum;
            }
        }
        return Objects.requireNonNull(result);
    }

    private ResultTemplateEntity matchTemplate(Long versionId, Long dimensionId, BigDecimal score) {
        List<ResultTemplateEntity> templates = this.resultTemplateMapper.selectList(
                Wrappers.<ResultTemplateEntity>lambdaQuery()
                        .eq(ResultTemplateEntity::getVersionId, versionId)
                        .eq(ResultTemplateEntity::getDimensionId, dimensionId)
                        .eq(ResultTemplateEntity::getDeleted, NORMAL)
                        .orderByAsc(ResultTemplateEntity::getSortNo)
                        .orderByAsc(ResultTemplateEntity::getId));
        return templates.stream()
                .filter(template -> score.compareTo(template.getScoreMin()) >= 0)
                .filter(template -> score.compareTo(template.getScoreMax()) < 0
                        || (score.compareTo(HUNDRED) == 0 && template.getScoreMax().compareTo(HUNDRED) == 0))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT.value(), "没有匹配的结果规则"));
    }

    private void tryGeneratePairReport(Long answerSessionId) {
        PairSessionEntity pair = this.pairSessionMapper.selectOne(Wrappers.<PairSessionEntity>lambdaQuery()
                .and(wrapper -> wrapper
                        .eq(PairSessionEntity::getInitiatorAnswerSessionId, answerSessionId)
                        .or()
                        .eq(PairSessionEntity::getPartnerAnswerSessionId, answerSessionId))
                .eq(PairSessionEntity::getDeleted, NORMAL)
                .orderByDesc(PairSessionEntity::getId)
                .last("LIMIT 1 FOR UPDATE"), false);
        if (Objects.isNull(pair) || Objects.isNull(pair.getPartnerAnswerSessionId())
                || PairStatus.CANCELLED.getCode() == pair.getPairStatus()
                || PairStatus.EXPIRED.getCode() == pair.getPairStatus()) {
            return;
        }
        PairReportEntity existing = this.pairReportMapper.selectOne(
                Wrappers.<PairReportEntity>lambdaQuery()
                        .eq(PairReportEntity::getPairSessionId, pair.getId())
                        .eq(PairReportEntity::getDeleted, NORMAL), false);
        if (Objects.nonNull(existing)) {
            this.updatePairStatus(pair.getId(), PairStatus.REPORT_READY);
            return;
        }
        List<Long> sessionIds = List.of(
                pair.getInitiatorAnswerSessionId(), pair.getPartnerAnswerSessionId());
        List<AnswerSessionEntity> sessions = this.baseMapper.selectByIds(sessionIds);
        boolean bothReady = sessions.size() == 2 && sessions.stream()
                .allMatch(session -> AnswerStatus.REPORT_READY.getCode() == session.getAnswerStatus());
        if (!bothReady) {
            return;
        }
        this.updatePairStatus(pair.getId(), PairStatus.BOTH_DONE);
        Map<Long, ReportEntity> reportMap = this.reportMapper.selectList(
                        Wrappers.<ReportEntity>lambdaQuery()
                                .in(ReportEntity::getAnswerSessionId, sessionIds)
                                .eq(ReportEntity::getDeleted, NORMAL))
                .stream()
                .collect(Collectors.toMap(ReportEntity::getAnswerSessionId, Function.identity()));
        if (reportMap.size() != 2) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "双方个人报告尚未生成");
        }
        JsonNode initiator = this.readJson(
                reportMap.get(pair.getInitiatorAnswerSessionId()).getResultSnapshot());
        JsonNode partner = this.readJson(
                reportMap.get(pair.getPartnerAnswerSessionId()).getResultSnapshot());
        ObjectNode aggregate = this.buildPairAggregate(initiator, partner);
        PairReportEntity pairReport = new PairReportEntity();
        pairReport.setReportNo(this.businessNo());
        pairReport.setPairSessionId(pair.getId());
        pairReport.setResultSnapshot(this.writeJson(aggregate));
        pairReport.setGeneratedAt(LocalDateTime.now());
        this.pairReportMapper.insertIgnore(pairReport);
        this.updatePairStatus(pair.getId(), PairStatus.REPORT_READY);
    }

    private ObjectNode buildPairAggregate(JsonNode initiator, JsonNode partner) {
        Map<String, BigDecimal> initiatorScores = this.dimensionScoreMap(initiator);
        Map<String, BigDecimal> partnerScores = this.dimensionScoreMap(partner);
        Set<String> commonDimensions = new HashSet<>(initiatorScores.keySet());
        commonDimensions.retainAll(partnerScores.keySet());
        BigDecimal averageGap = commonDimensions.stream()
                .map(code -> initiatorScores.get(code).subtract(partnerScores.get(code)).abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!commonDimensions.isEmpty()) {
            averageGap = averageGap.divide(BigDecimal.valueOf(commonDimensions.size()), 2, RoundingMode.HALF_UP);
        }
        boolean closeMatch = averageGap.compareTo(BigDecimal.valueOf(20)) <= 0;
        ObjectNode aggregate = this.objectMapper.createObjectNode();
        aggregate.put("roleName", closeMatch ? "默契搭子" : "互补搭子");
        aggregate.put("summary", closeMatch
                ? "你们在一些核心倾向上较为接近，也可以继续聊聊彼此不同的感受。"
                : "你们在部分倾向上各有特点，主动表达会更容易理解彼此。");
        if (closeMatch) {
            aggregate.putArray("actions")
                    .add("有分歧也愿意说清楚")
                    .add("会把彼此的感受放进计划")
                    .add("能在日常里给对方稳定回应");
        } else {
            aggregate.putArray("actions")
                    .add("先说清彼此不同的期待")
                    .add("把差异变成可以商量的选择")
                    .add("给对方留出适合自己的节奏");
        }
        aggregate.set("initiatorResult", this.basicResultIdentity(initiator));
        aggregate.set("partnerResult", this.basicResultIdentity(partner));
        return aggregate;
    }

    private ObjectNode basicResultIdentity(JsonNode snapshot) {
        ObjectNode result = this.objectMapper.createObjectNode();
        result.put("resultCode", snapshot.path("resultCode").asText());
        result.put("resultName", snapshot.path("resultName").asText());
        return result;
    }

    private Map<String, BigDecimal> dimensionScoreMap(JsonNode snapshot) {
        Map<String, BigDecimal> scores = new HashMap<>();
        snapshot.path("dimensions").forEach(node -> scores.put(
                node.path("dimensionCode").asText(), node.path("normalizedScore").decimalValue()));
        return scores;
    }

    private void updatePairStatus(Long pairId, PairStatus status) {
        this.pairSessionMapper.update(null,
                Wrappers.<PairSessionEntity>lambdaUpdate()
                        .set(PairSessionEntity::getPairStatus, status.getCode())
                        .eq(PairSessionEntity::getId, pairId)
                        .eq(PairSessionEntity::getDeleted, NORMAL));
    }

    private List<AnswerSessionQuestionEntity> listSnapshots(Long sessionId) {
        return this.answerSessionQuestionMapper.selectList(
                Wrappers.<AnswerSessionQuestionEntity>lambdaQuery()
                        .eq(AnswerSessionQuestionEntity::getAnswerSessionId, sessionId)
                        .eq(AnswerSessionQuestionEntity::getDeleted, NORMAL)
                        .orderByAsc(AnswerSessionQuestionEntity::getSortNo));
    }

    private List<QuestionEntity> listQuestions(List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return this.questionMapper.selectList(
                Wrappers.<QuestionEntity>lambdaQuery()
                        .in(QuestionEntity::getId, questionIds)
                        .eq(QuestionEntity::getDeleted, NORMAL));
    }

    private List<QuestionOptionEntity> listOptions(List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return this.questionOptionMapper.selectList(
                Wrappers.<QuestionOptionEntity>lambdaQuery()
                        .in(QuestionOptionEntity::getQuestionId, questionIds)
                        .eq(QuestionOptionEntity::getDeleted, NORMAL)
                        .orderByDesc(QuestionOptionEntity::getSortNo)
                        .orderByAsc(QuestionOptionEntity::getId));
    }

    private List<AnswerDetailEntity> listAnswerDetails(Long sessionId) {
        return this.answerDetailMapper.selectList(
                Wrappers.<AnswerDetailEntity>lambdaQuery()
                        .eq(AnswerDetailEntity::getAnswerSessionId, sessionId)
                        .eq(AnswerDetailEntity::getDeleted, NORMAL));
    }

    private List<ScoreDimensionEntity> listDimensions(Long versionId) {
        return this.scoreDimensionMapper.selectList(
                Wrappers.<ScoreDimensionEntity>lambdaQuery()
                        .eq(ScoreDimensionEntity::getVersionId, versionId)
                        .eq(ScoreDimensionEntity::getDeleted, NORMAL)
                        .orderByAsc(ScoreDimensionEntity::getSortNo)
                        .orderByAsc(ScoreDimensionEntity::getId));
    }

    private AnswerSessionEntity requireOwnedSession(Long sessionId, String openId) {
        AnswerSessionEntity session = this.getById(sessionId);
        if (Objects.isNull(session) || !Objects.equals(session.getOpenId(), openId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.value(), "答卷不存在");
        }
        return session;
    }

    private AnswerSessionEntity requireOwnedLockedSession(Long sessionId, String openId) {
        AnswerSessionEntity session = this.lambdaQuery()
                .eq(AnswerSessionEntity::getId, sessionId)
                .eq(AnswerSessionEntity::getDeleted, NORMAL)
                .last("FOR UPDATE")
                .one();
        if (Objects.isNull(session) || !Objects.equals(session.getOpenId(), openId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.value(), "答卷不存在");
        }
        return session;
    }

    private void requireInProgress(AnswerSessionEntity session) {
        if (AnswerStatus.IN_PROGRESS.getCode() != session.getAnswerStatus()) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "答卷当前状态不允许作答");
        }
    }

    private TestVersionEntity requireVersion(Long versionId) {
        TestVersionEntity version = this.testVersionMapper.selectById(versionId);
        if (Objects.isNull(version)) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "答卷关联版本不存在");
        }
        return version;
    }

    private ReportEntity findReportBySessionId(Long sessionId) {
        return this.reportMapper.selectOne(
                Wrappers.<ReportEntity>lambdaQuery()
                        .eq(ReportEntity::getAnswerSessionId, sessionId)
                        .eq(ReportEntity::getDeleted, NORMAL), false);
    }

    private void validateSelectionCount(QuestionEntity question, int selectedCount) {
        QuestionType.of(question.getQuestionType());
        if (selectedCount == 0 && ENABLED != question.getRequiredFlag()) {
            return;
        }
        if (QuestionType.SINGLE.getCode() == question.getQuestionType() && selectedCount != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "单选题只能选择一个选项");
        }
        if (QuestionType.MULTIPLE.getCode() == question.getQuestionType()
                && (selectedCount < question.getMinSelectCount()
                || selectedCount > question.getMaxSelectCount())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "多选题选择数量不合法");
        }
    }

    private BigDecimal scoreValue(QuestionOptionEntity option) {
        if (Objects.isNull(option.getScoreValue())) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "选项计分配置无效");
        }
        return BigDecimal.valueOf(option.getScoreValue());
    }

    private int dimensionSort(List<ScoreDimensionEntity> dimensions, String dimensionId) {
        return dimensions.stream()
                .filter(dimension -> Objects.equals(String.valueOf(dimension.getId()), dimensionId))
                .map(ScoreDimensionEntity::getSortNo)
                .findFirst()
                .orElse(Integer.MAX_VALUE);
    }

    private ReportResponse toSubmissionResponse(String openId, ReportEntity report) {
        if (!this.reportAccessService.canRead(openId, ReportKind.SINGLE.getCode(), report.getId())) {
            return new ReportResponse(String.valueOf(report.getId()),
                    String.valueOf(report.getAnswerSessionId()), null, null, report.getGeneratedAt());
        }
        return this.toReportResponse(report);
    }

    private ReportResponse toReportResponse(ReportEntity report) {
        JsonNode resultSnapshot = this.readJson(report.getResultSnapshot());
        if (resultSnapshot instanceof ObjectNode objectNode) {
            objectNode.remove("deepResult");
        }
        return new ReportResponse(
                String.valueOf(report.getId()),
                String.valueOf(report.getAnswerSessionId()),
                report.getResultCode(),
                resultSnapshot,
                report.getGeneratedAt());
    }

    private ReportHistoryResponse toHistoryResponse(ReportHistoryRow row) {
        return new ReportHistoryResponse(
                String.valueOf(row.getReportId()),
                String.valueOf(row.getAnswerSessionId()),
                String.valueOf(row.getTestId()),
                row.getVersionNo(),
                row.getTitle(),
                row.getResultCode(),
                row.getResultName(),
                row.getGeneratedAt());
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "ID 格式错误");
        }
    }

    private JsonNode readJson(String value) {
        if (Objects.isNull(value)) {
            return NullNode.getInstance();
        }
        try {
            return this.objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(HttpStatus.CONFLICT.value(), "报告快照格式错误");
        }
    }

    private String writeJson(Object value) {
        try {
            return this.objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR.value(), "报告快照生成失败");
        }
    }

    private String businessNo() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
