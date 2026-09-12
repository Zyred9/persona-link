package com.personalink.server.service.impl;

import lombok.RequiredArgsConstructor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.entity.CategoryEntity;
import com.personalink.server.service.CategoryService;
import com.personalink.server.dto.PageResponse;
import com.personalink.server.dto.AuditLogQuery;
import com.personalink.server.dto.AuditLogResponse;
import com.personalink.server.dto.PublishCheckResponse;
import com.personalink.server.dto.QuestionOptionResponse;
import com.personalink.server.dto.QuestionOptionSaveRequest;
import com.personalink.server.dto.QuestionResponse;
import com.personalink.server.dto.QuestionSaveRequest;
import com.personalink.server.dto.ResultConfigResponse;
import com.personalink.server.dto.ResultConfigSaveRequest;
import com.personalink.server.dto.ResultTemplateResponse;
import com.personalink.server.dto.ResultTemplateSaveRequest;
import com.personalink.server.dto.ScoreDimensionResponse;
import com.personalink.server.dto.ScoreDimensionSaveRequest;
import com.personalink.server.dto.TestQuery;
import com.personalink.server.dto.TestResponse;
import com.personalink.server.dto.TestSaveRequest;
import com.personalink.server.dto.TestVersionResponse;
import com.personalink.server.dto.TestVersionSaveRequest;
import com.personalink.server.entity.ContentAuditLogEntity;
import com.personalink.server.entity.QuestionEntity;
import com.personalink.server.entity.QuestionOptionEntity;
import com.personalink.server.entity.ResultTemplateEntity;
import com.personalink.server.entity.ScoreDimensionEntity;
import com.personalink.server.entity.TestEntity;
import com.personalink.server.entity.TestVersionEntity;
import com.personalink.server.mapper.ContentAuditLogMapper;
import com.personalink.server.mapper.QuestionMapper;
import com.personalink.server.mapper.QuestionOptionMapper;
import com.personalink.server.mapper.ResultTemplateMapper;
import com.personalink.server.mapper.ScoreDimensionMapper;
import com.personalink.server.mapper.TestMapper;
import com.personalink.server.mapper.TestVersionMapper;
import com.personalink.server.service.ContentService;
import com.personalink.server.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 人工内容运营业务实现。 */
@Service
@RequiredArgsConstructor
public class ContentServiceImpl extends ServiceImpl<TestMapper, TestEntity> implements ContentService {

    private static final int NORMAL = 0;
    private static final int DELETED = 1;
    private static final int DISABLED = 0;
    private static final int ENABLED = 1;
    private static final int DRAFT = 1;
    private static final int PENDING = 3;
    private static final int PUBLISHED = 4;
    private static final int OFFLINE = 5;
    private static final int ARCHIVED = 6;
    private static final int SINGLE = 1;
    private static final int MULTIPLE = 2;
    private static final int HOME_REGULAR = 0;
    private static final int HOME_FOCUS = 1;
    private static final int BIZ_TEST = 1;
    private static final int BIZ_VERSION = 2;
    private static final int ACTION_CREATE = 1;
    private static final int ACTION_UPDATE = 2;
    private static final int ACTION_STATUS = 3;
    private static final int ACTION_PUBLISH = 4;
    private static final int ACTION_OFFLINE = 5;
    private static final int ACTION_DELETE = 6;
    private static final int ACTION_SCHEDULE = 7;
    private static final int ACTION_CANCEL_SCHEDULE = 8;
    private static final int ACTION_ARCHIVE = 9;
    private static final long SYSTEM_OPERATOR_ID = 0L;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final TestVersionMapper versionMapper;
    private final ScoreDimensionMapper dimensionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final ResultTemplateMapper resultMapper;
    private final ContentAuditLogMapper auditMapper;
    private final CategoryService categoryService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TestResponse> pageTests(TestQuery query) {
        long total = this.baseMapper.countTestPage(query);
        long offset = (query.getPage() - 1) * query.getSize();
        return new PageResponse<>(this.baseMapper.selectTestPage(query, offset, query.getSize()),
                total, query.getPage(), query.getSize());
    }

    @Override
    @Transactional(readOnly = true)
    public TestResponse getTest(Long id) {
        TestResponse response = this.baseMapper.selectTestDetail(id);
        if (Objects.isNull(response)) {
            throw this.notFound("题型不存在");
        }
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestResponse createTest(TestSaveRequest request, Long operatorId) {
        this.requireActiveCategory(request.categoryId());
        TestEntity entity = new TestEntity();
        this.applyTest(entity, request);
        entity.setDeleted(NORMAL);
        this.save(entity);
        this.writeAudit(BIZ_TEST, entity.getId(), ACTION_CREATE, null, entity, operatorId, null);
        return this.getTest(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestResponse updateTest(Long id, TestSaveRequest request, Long operatorId) {
        TestEntity before = this.selectTestForUpdate(id);
        if (Objects.isNull(before)) {
            throw this.notFound("题型不存在");
        }
        this.requireActiveCategory(request.categoryId());
        if (!Objects.equals(before.getTestType(), request.testType())
                && this.versionMapper.selectCount(Wrappers.<TestVersionEntity>lambdaQuery()
                .eq(TestVersionEntity::getTestId, id)
                .and(version -> version.isNotNull(TestVersionEntity::getPublishedAt)
                        .or().in(TestVersionEntity::getVersionStatus, PUBLISHED, OFFLINE, ARCHIVED))) > 0) {
            throw this.badRequest("题型已有发布历史，不能修改单人或双人类型");
        }
        TestEntity entity = new TestEntity();
        entity.setId(id);
        this.applyTest(entity, request);
        if (DISABLED == request.status()) {
            entity.setHomeDisplay(HOME_REGULAR);
            entity.setHomeSort(HOME_REGULAR);
        }
        this.updateById(entity);
        this.writeAudit(BIZ_TEST, id, ACTION_UPDATE, before, entity, operatorId, null);
        return this.getTest(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestResponse updateTestStatus(Long id, Integer status, Long operatorId) {
        TestEntity before = this.selectTestForUpdate(id);
        if (Objects.isNull(before)) {
            throw this.notFound("题型不存在");
        }
        var update = this.lambdaUpdate()
                .set(TestEntity::getStatus, status)
                .eq(TestEntity::getId, id)
                .eq(TestEntity::getDeleted, NORMAL);
        if (DISABLED == status) {
            update.set(TestEntity::getHomeDisplay, HOME_REGULAR)
                    .set(TestEntity::getHomeSort, HOME_REGULAR);
        }
        update.update();
        this.writeAudit(BIZ_TEST, id, ACTION_STATUS, before, this.getTestEntity(id), operatorId, null);
        return this.getTest(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestResponse updateTestHomeDisplay(Long id, Integer homeDisplay, Integer homeSort, Long operatorId) {
        TestEntity before = this.getTestEntity(id);
        if (HOME_REGULAR != homeDisplay) {
            if (ENABLED != before.getStatus()) {
                throw this.badRequest("停用题型不能配置到小程序首页推荐位");
            }
            this.requireActiveCategory(before.getCategoryId());
            long publishedCount = this.versionMapper.selectCount(
                    Wrappers.<TestVersionEntity>lambdaQuery()
                            .eq(TestVersionEntity::getTestId, id)
                            .eq(TestVersionEntity::getVersionStatus, PUBLISHED)
                            .eq(TestVersionEntity::getDeleted, NORMAL));
            if (publishedCount == 0) {
                throw this.badRequest("题型必须先发布可用版本，才能配置到小程序首页推荐位");
            }
        }
        if (HOME_FOCUS == homeDisplay) {
            List<TestEntity> previousFocus = this.list(Wrappers.<TestEntity>lambdaQuery()
                    .ne(TestEntity::getId, id)
                    .eq(TestEntity::getHomeDisplay, HOME_FOCUS)
                    .eq(TestEntity::getDeleted, NORMAL));
            this.lambdaUpdate()
                    .set(TestEntity::getHomeDisplay, HOME_REGULAR)
                    .set(TestEntity::getHomeSort, HOME_REGULAR)
                    .ne(TestEntity::getId, id)
                    .eq(TestEntity::getHomeDisplay, HOME_FOCUS)
                    .eq(TestEntity::getDeleted, NORMAL)
                    .update();
            List<ContentAuditLogEntity> audits = previousFocus.stream().map(previous -> {
                TestEntity after = new TestEntity();
                after.setId(previous.getId());
                after.setHomeDisplay(HOME_REGULAR);
                after.setHomeSort(HOME_REGULAR);
                return this.buildAudit(BIZ_TEST, previous.getId(), ACTION_UPDATE, previous, after,
                        operatorId, "焦点位被题型 " + id + " 替换");
            }).toList();
            if (!audits.isEmpty()) {
                this.auditMapper.insert(audits);
            }
        }
        this.lambdaUpdate()
                .set(TestEntity::getHomeDisplay, homeDisplay)
                .set(TestEntity::getHomeSort, homeSort)
                .eq(TestEntity::getId, id)
                .eq(TestEntity::getDeleted, NORMAL)
                .update();
        this.writeAudit(BIZ_TEST, id, ACTION_UPDATE, before, this.getTestEntity(id), operatorId, "更新首页展示");
        return this.getTest(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTests(List<Long> ids, Long operatorId) {
        Set<Long> uniqueIds = new HashSet<>(ids);
        List<TestEntity> tests = this.list(Wrappers.<TestEntity>lambdaQuery()
                .in(TestEntity::getId, uniqueIds)
                .eq(TestEntity::getDeleted, NORMAL));
        if (tests.size() != uniqueIds.size()) {
            throw this.badRequest("题型不存在或已删除");
        }
        this.lambdaUpdate()
                .set(TestEntity::getDeleted, DELETED)
                .in(TestEntity::getId, uniqueIds)
                .eq(TestEntity::getDeleted, NORMAL)
                .update();
        List<ContentAuditLogEntity> audits = tests.stream()
                .map(test -> this.buildAudit(BIZ_TEST, test.getId(), ACTION_DELETE,
                        test, null, operatorId, "删除题型"))
                .toList();
        this.auditMapper.insert(audits);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestVersionResponse> listVersions(Long testId) {
        this.getTestEntity(testId);
        List<TestVersionEntity> versions = this.versionMapper.selectList(
                Wrappers.<TestVersionEntity>lambdaQuery()
                        .eq(TestVersionEntity::getTestId, testId)
                        .eq(TestVersionEntity::getDeleted, NORMAL)
                        .orderByDesc(TestVersionEntity::getVersionNo));
        if (versions.isEmpty()) {
            return List.of();
        }
        List<Long> versionIds = versions.stream().map(TestVersionEntity::getId).toList();
        Map<Long, List<ScoreDimensionEntity>> dimensionMap = this.dimensionMapper.selectList(
                        Wrappers.<ScoreDimensionEntity>lambdaQuery()
                                .in(ScoreDimensionEntity::getVersionId, versionIds)
                                .eq(ScoreDimensionEntity::getDeleted, NORMAL)
                                .orderByAsc(ScoreDimensionEntity::getSortNo))
                .stream().collect(Collectors.groupingBy(ScoreDimensionEntity::getVersionId));
        Map<Long, Long> questionCountMap = this.questionMapper.selectList(
                        Wrappers.<QuestionEntity>lambdaQuery()
                                .in(QuestionEntity::getVersionId, versionIds)
                                .eq(QuestionEntity::getDeleted, NORMAL))
                .stream().collect(Collectors.groupingBy(QuestionEntity::getVersionId, Collectors.counting()));
        return versions.stream().map(version -> this.toVersionResponse(version,
                dimensionMap.getOrDefault(version.getId(), List.of()),
                questionCountMap.getOrDefault(version.getId(), 0L))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TestVersionResponse getVersion(Long versionId) {
        TestVersionEntity version = this.getVersionEntity(versionId);
        List<ScoreDimensionEntity> dimensions = this.listDimensions(versionId);
        long questionCount = this.questionMapper.selectCount(Wrappers.<QuestionEntity>lambdaQuery()
                .eq(QuestionEntity::getVersionId, versionId)
                .eq(QuestionEntity::getDeleted, NORMAL));
        return this.toVersionResponse(version, dimensions, questionCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestVersionResponse createVersion(Long testId, TestVersionSaveRequest request, Long operatorId) {
        if (Objects.isNull(this.selectTestForUpdate(testId))) {
            throw this.notFound("题型不存在");
        }
        Integer maxVersionNo = this.selectMaxVersionNo(testId);
        TestVersionEntity version = new TestVersionEntity();
        version.setTestId(testId);
        version.setVersionNo(Objects.isNull(maxVersionNo) ? 1 : maxVersionNo + 1);
        version.setVersionStatus(DRAFT);
        version.setRiskOfflineFlag(NORMAL);
        version.setDeleted(NORMAL);
        this.applyVersion(version, request);
        this.versionMapper.insert(version);
        List<ScoreDimensionEntity> dimensions = this.buildDimensions(version.getId(), request.dimensions(), Map.of());
        if (!dimensions.isEmpty()) {
            this.dimensionMapper.insertBatch(dimensions);
        }
        this.writeAudit(BIZ_VERSION, version.getId(), ACTION_CREATE, null, version, operatorId, null);
        return this.getVersion(version.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TestVersionResponse copyVersionAsDraft(Long versionId, Long operatorId) {
        // 与创建草稿、采用图片保持相同锁顺序：先题型，后版本。
        this.lockTestForVersion(versionId);
        TestVersionEntity source = this.selectVersionForUpdate(versionId);
        if (Objects.isNull(source)) {
            throw this.notFound("题型版本不存在");
        }
        if (PUBLISHED != source.getVersionStatus() && OFFLINE != source.getVersionStatus()
                && ARCHIVED != source.getVersionStatus()) {
            throw this.badRequest("只有已发布、已下线或已归档版本可以复制为草稿");
        }
        TestVersionEntity existingDraft = this.versionMapper.selectOne(
                Wrappers.<TestVersionEntity>lambdaQuery()
                        .eq(TestVersionEntity::getTestId, source.getTestId())
                        .eq(TestVersionEntity::getVersionStatus, DRAFT)
                        .eq(TestVersionEntity::getDeleted, NORMAL)
                        .orderByDesc(TestVersionEntity::getVersionNo)
                        .last("LIMIT 1 FOR UPDATE"), false);
        if (Objects.nonNull(existingDraft)) {
            throw this.badRequest("该题型已有编辑中草稿");
        }
        Integer maxVersionNo = this.selectMaxVersionNo(source.getTestId());
        TestVersionEntity draft = new TestVersionEntity();
        draft.setTestId(source.getTestId());
        draft.setVersionNo(Objects.isNull(maxVersionNo) ? 1 : maxVersionNo + 1);
        draft.setTitle(source.getTitle());
        draft.setCoverUrl(source.getCoverUrl());
        draft.setDetailImageUrl(source.getDetailImageUrl());
        draft.setDescription(source.getDescription());
        draft.setEstimatedMinutes(source.getEstimatedMinutes());
        draft.setDrawQuestionCount(source.getDrawQuestionCount());
        draft.setVersionStatus(DRAFT);
        draft.setVersionNote(source.getVersionNote());
        draft.setRiskOfflineFlag(NORMAL);
        draft.setDeleted(NORMAL);
        this.versionMapper.insert(draft);
        this.versionMapper.cloneDimensions(source.getId(), draft.getId());
        this.versionMapper.cloneQuestions(source.getId(), draft.getId());
        this.versionMapper.cloneOptions(source.getId(), draft.getId());
        this.versionMapper.cloneResults(source.getId(), draft.getId());
        this.writeAudit(BIZ_VERSION, draft.getId(), ACTION_CREATE, source, draft,
                operatorId, "复制历史版本为草稿");
        return this.getVersion(draft.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long applyGeneratedImages(Long testId, String coverUrl, String detailImageUrl, Long operatorId) {
        if (!this.hasText(coverUrl) || !this.hasText(detailImageUrl)
                || coverUrl.length() > 500 || detailImageUrl.length() > 500) {
            throw this.badRequest("生成图片地址无效");
        }
        if (Objects.isNull(this.selectTestForUpdate(testId))) {
            throw this.notFound("题型不存在");
        }
        TestVersionEntity draft = this.versionMapper.selectOne(Wrappers.<TestVersionEntity>lambdaQuery()
                .eq(TestVersionEntity::getTestId, testId)
                .eq(TestVersionEntity::getVersionStatus, DRAFT)
                .eq(TestVersionEntity::getDeleted, NORMAL)
                .orderByDesc(TestVersionEntity::getVersionNo)
                .last("LIMIT 1 FOR UPDATE"), false);
        if (Objects.isNull(draft)) {
            TestVersionEntity source = this.selectPublishedVersionForUpdate(testId);
            if (Objects.isNull(source)) {
                source = this.versionMapper.selectOne(Wrappers.<TestVersionEntity>lambdaQuery()
                        .eq(TestVersionEntity::getTestId, testId)
                        .eq(TestVersionEntity::getVersionStatus, OFFLINE)
                        .eq(TestVersionEntity::getDeleted, NORMAL)
                        .orderByDesc(TestVersionEntity::getVersionNo)
                        .last("LIMIT 1 FOR UPDATE"), false);
            }
            if (Objects.isNull(source)) {
                throw this.badRequest("请先创建题型草稿，再采用生成图片");
            }
            draft = this.getVersionEntity(this.copyVersionAsDraft(source.getId(), operatorId).id());
        }
        // 使用局部更新，避免实体的 ALWAYS 空值策略清空文案和其他版本字段。
        int changed = this.versionMapper.update(null, Wrappers.<TestVersionEntity>lambdaUpdate()
                .set(TestVersionEntity::getCoverUrl, coverUrl)
                .set(TestVersionEntity::getDetailImageUrl, detailImageUrl)
                .eq(TestVersionEntity::getId, draft.getId())
                .eq(TestVersionEntity::getTestId, testId)
                .eq(TestVersionEntity::getVersionStatus, DRAFT)
                .eq(TestVersionEntity::getDeleted, NORMAL));
        if (changed != 1) {
            throw this.badRequest("草稿状态已变化，请刷新后重试");
        }
        this.writeAudit(BIZ_VERSION, draft.getId(), ACTION_UPDATE, draft,
                this.getVersionEntity(draft.getId()), operatorId, "采用 AI 封面图和详情图");
        return draft.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestVersionResponse updateVersion(Long versionId, TestVersionSaveRequest request, Long operatorId) {
        TestVersionEntity before = this.requireDraftVersionForUpdate(versionId);
        List<ScoreDimensionEntity> existing = this.listDimensions(versionId);
        Map<Long, ScoreDimensionEntity> existingMap = existing.stream()
                .collect(Collectors.toMap(ScoreDimensionEntity::getId, Function.identity()));
        List<ScoreDimensionEntity> requested = this.buildDimensions(versionId, request.dimensions(), existingMap);
        boolean hasContent = this.questionMapper.selectCount(Wrappers.<QuestionEntity>lambdaQuery()
                .eq(QuestionEntity::getVersionId, versionId).eq(QuestionEntity::getDeleted, NORMAL)) > 0
                || this.resultMapper.selectCount(Wrappers.<ResultTemplateEntity>lambdaQuery()
                .eq(ResultTemplateEntity::getVersionId, versionId).eq(ResultTemplateEntity::getDeleted, NORMAL)) > 0;
        Set<Long> requestIds = request.dimensions().stream().map(ScoreDimensionSaveRequest::id)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (hasContent && (requestIds.size() != existing.size() || !requestIds.equals(existingMap.keySet()))) {
            throw this.badRequest("题目或结果已引用计分维度，不能增删维度");
        }
        boolean sameDimensionIds = !existing.isEmpty()
                && requestIds.size() == existing.size()
                && requestIds.equals(existingMap.keySet());
        if (hasContent || sameDimensionIds) {
            this.dimensionMapper.updateBatch(requested);
        } else {
            if (!existing.isEmpty()) {
                this.dimensionMapper.update(null, Wrappers.<ScoreDimensionEntity>lambdaUpdate()
                        .set(ScoreDimensionEntity::getDeleted, DELETED)
                        .eq(ScoreDimensionEntity::getVersionId, versionId)
                        .eq(ScoreDimensionEntity::getDeleted, NORMAL));
            }
            requested.forEach(dimension -> dimension.setId(null));
            this.dimensionMapper.insertBatch(requested);
        }
        TestVersionEntity update = new TestVersionEntity();
        update.setId(versionId);
        this.applyVersion(update, request);
        this.versionMapper.updateById(update);
        this.writeAudit(BIZ_VERSION, versionId, ACTION_UPDATE, before, update, operatorId, null);
        return this.getVersion(versionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionResponse> listQuestions(Long versionId) {
        this.getVersionEntity(versionId);
        List<QuestionEntity> questions = this.questionMapper.selectList(Wrappers.<QuestionEntity>lambdaQuery()
                .eq(QuestionEntity::getVersionId, versionId)
                .eq(QuestionEntity::getDeleted, NORMAL)
                .orderByAsc(QuestionEntity::getQuestionNo));
        if (questions.isEmpty()) {
            return List.of();
        }
        List<Long> questionIds = questions.stream().map(QuestionEntity::getId).toList();
        Map<Long, List<QuestionOptionEntity>> optionMap = this.optionMapper.selectList(
                        Wrappers.<QuestionOptionEntity>lambdaQuery()
                                .in(QuestionOptionEntity::getQuestionId, questionIds)
                                .eq(QuestionOptionEntity::getDeleted, NORMAL)
                                .orderByDesc(QuestionOptionEntity::getSortNo)
                                .orderByAsc(QuestionOptionEntity::getId))
                .stream().collect(Collectors.groupingBy(QuestionOptionEntity::getQuestionId));
        return questions.stream().map(question -> this.toQuestionResponse(question,
                optionMap.getOrDefault(question.getId(), List.of()))).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionResponse createQuestion(Long versionId, QuestionSaveRequest request, Long operatorId) {
        this.requireDraftVersionForUpdate(versionId);
        this.validateQuestion(versionId, null, request);
        QuestionEntity question = this.buildQuestion(versionId, null, request);
        this.questionMapper.insert(question);
        List<QuestionOptionEntity> options = this.buildOptions(question.getId(), request);
        this.optionMapper.insertBatch(options);
        this.writeAudit(BIZ_VERSION, versionId, ACTION_UPDATE, null, question, operatorId, "新增题目");
        return this.getQuestion(question.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int appendGeneratedQuestions(Long versionId,
                                         List<QuestionSaveRequest> requests,
                                         Long operatorId) {
        this.requireDraftVersionForUpdate(versionId);
        if (requests.isEmpty()) {
            throw this.badRequest("AI 生成题目批次不能为空");
        }
        Set<Integer> questionNos = requests.stream().map(QuestionSaveRequest::questionNo)
                .collect(Collectors.toSet());
        if (questionNos.size() != requests.size()) {
            throw this.badRequest("AI 生成题号不能重复");
        }
        long duplicateNumberCount = this.questionMapper.selectCount(Wrappers.<QuestionEntity>lambdaQuery()
                .eq(QuestionEntity::getVersionId, versionId)
                .in(QuestionEntity::getQuestionNo, questionNos)
                .eq(QuestionEntity::getDeleted, NORMAL));
        if (duplicateNumberCount > 0) {
            throw this.badRequest("AI 生成题号已存在，请检查题库与任务进度");
        }
        Set<Integer> conflictingNos = new java.util.HashSet<>(
                this.questionMapper.selectConflictingGeneratedQuestionNos(versionId, requests));
        requests = requests.stream().filter(request -> !conflictingNos.contains(request.questionNo())).toList();
        if (requests.isEmpty()) {
            return 0;
        }
        questionNos = requests.stream().map(QuestionSaveRequest::questionNo).collect(Collectors.toSet());
        Set<Long> dimensionIds = this.listDimensions(versionId).stream()
                .map(ScoreDimensionEntity::getId).collect(Collectors.toSet());
        requests.forEach(request -> this.validateQuestionContent(request, dimensionIds));
        List<QuestionEntity> questions = requests.stream()
                .map(request -> this.buildQuestion(versionId, null, request)).toList();
        this.questionMapper.insertBatch(questions);
        Map<Integer, Long> questionIdMap = this.questionMapper.selectList(
                        Wrappers.<QuestionEntity>lambdaQuery()
                                .eq(QuestionEntity::getVersionId, versionId)
                                .in(QuestionEntity::getQuestionNo, questionNos)
                                .eq(QuestionEntity::getDeleted, NORMAL))
                .stream().collect(Collectors.toMap(QuestionEntity::getQuestionNo, QuestionEntity::getId));
        if (questionIdMap.size() != requests.size()) {
            throw new IllegalStateException("AI 生成题目批量落库数量不一致");
        }
        List<QuestionOptionEntity> options = requests.stream()
                .flatMap(request -> this.buildOptions(questionIdMap.get(request.questionNo()), request).stream())
                .toList();
        this.optionMapper.insertBatch(options);
        this.writeAudit(BIZ_VERSION, versionId, ACTION_UPDATE, null, questions,
                operatorId, "AI 批量生成题目");
        return questions.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionResponse updateQuestion(Long questionId, QuestionSaveRequest request, Long operatorId) {
        QuestionEntity before = this.getQuestionEntity(questionId);
        this.requireDraftVersionForUpdate(before.getVersionId());
        this.validateQuestion(before.getVersionId(), questionId, request);
        QuestionEntity update = this.buildQuestion(before.getVersionId(), questionId, request);
        this.questionMapper.updateById(update);
        this.optionMapper.update(null, Wrappers.<QuestionOptionEntity>lambdaUpdate()
                .set(QuestionOptionEntity::getDeleted, DELETED)
                .eq(QuestionOptionEntity::getQuestionId, questionId)
                .eq(QuestionOptionEntity::getDeleted, NORMAL));
        this.optionMapper.insertBatch(this.buildOptions(questionId, request));
        this.writeAudit(BIZ_VERSION, before.getVersionId(), ACTION_UPDATE, before, update, operatorId, "更新题目");
        return this.getQuestion(questionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestion(Long questionId, Long operatorId) {
        QuestionEntity question = this.getQuestionEntity(questionId);
        this.requireDraftVersionForUpdate(question.getVersionId());
        this.questionMapper.update(null, Wrappers.<QuestionEntity>lambdaUpdate()
                .set(QuestionEntity::getDeleted, DELETED)
                .eq(QuestionEntity::getId, questionId)
                .eq(QuestionEntity::getDeleted, NORMAL));
        this.optionMapper.update(null, Wrappers.<QuestionOptionEntity>lambdaUpdate()
                .set(QuestionOptionEntity::getDeleted, DELETED)
                .eq(QuestionOptionEntity::getQuestionId, questionId)
                .eq(QuestionOptionEntity::getDeleted, NORMAL));
        this.writeAudit(BIZ_VERSION, question.getVersionId(), ACTION_UPDATE, question, null, operatorId, "删除题目");
    }

    @Override
    @Transactional(readOnly = true)
    public ResultConfigResponse getResultConfig(Long versionId) {
        this.getVersionEntity(versionId);
        List<ScoreDimensionEntity> dimensions = this.listDimensions(versionId);
        List<ResultTemplateEntity> templates = this.resultMapper.selectList(
                Wrappers.<ResultTemplateEntity>lambdaQuery()
                        .eq(ResultTemplateEntity::getVersionId, versionId)
                        .eq(ResultTemplateEntity::getDeleted, NORMAL)
                        .orderByAsc(ResultTemplateEntity::getDimensionId)
                        .orderByAsc(ResultTemplateEntity::getSortNo));
        return new ResultConfigResponse(versionId, dimensions.stream().map(this::toDimensionResponse).toList(),
                templates.stream().map(this::toResultResponse).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultConfigResponse saveResultConfig(Long versionId, ResultConfigSaveRequest request, Long operatorId) {
        this.requireDraftVersionForUpdate(versionId);
        List<ScoreDimensionEntity> dimensions = this.listDimensions(versionId);
        List<ResultTemplateEntity> templates = this.buildResults(versionId, dimensions, request.templates());
        this.validateResultCoverage(dimensions, templates);
        List<ResultTemplateEntity> before = this.resultMapper.selectList(
                Wrappers.<ResultTemplateEntity>lambdaQuery()
                        .eq(ResultTemplateEntity::getVersionId, versionId)
                        .eq(ResultTemplateEntity::getDeleted, NORMAL));
        if (!before.isEmpty()) {
            this.resultMapper.update(null, Wrappers.<ResultTemplateEntity>lambdaUpdate()
                    .set(ResultTemplateEntity::getDeleted, DELETED)
                    .eq(ResultTemplateEntity::getVersionId, versionId)
                    .eq(ResultTemplateEntity::getDeleted, NORMAL));
        }
        this.resultMapper.insertBatch(templates);
        this.writeAudit(BIZ_VERSION, versionId, ACTION_UPDATE, before, templates, operatorId, "保存结果配置");
        return this.getResultConfig(versionId);
    }

    @Override
    @Transactional(readOnly = true)
    public PublishCheckResponse checkPublish(Long versionId) {
        return this.validatePublish(this.getVersionEntity(versionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TestVersionResponse schedule(Long versionId, LocalDateTime scheduledAt, Long operatorId) {
        // 等待父行锁后按最新已提交内容校验，不能沿用锁前的 RR 快照。
        this.lockTestForVersion(versionId);
        TestVersionEntity target = this.selectVersionForUpdate(versionId);
        if (Objects.isNull(target)) {
            throw this.notFound("题型版本不存在");
        }
        if (DRAFT != target.getVersionStatus()) {
            throw this.badRequest("只有草稿版本可以设置发布排期");
        }
        if (!scheduledAt.isAfter(LocalDateTime.now())) {
            throw this.badRequest("计划发布时间必须晚于当前时间");
        }
        PublishCheckResponse check = this.validatePublish(target);
        if (!check.passed()) {
            throw this.badRequest(String.join("；", check.errors()));
        }
        TestVersionEntity pending = this.versionMapper.selectOne(Wrappers.<TestVersionEntity>lambdaQuery()
                .eq(TestVersionEntity::getTestId, target.getTestId())
                .eq(TestVersionEntity::getVersionStatus, PENDING)
                .eq(TestVersionEntity::getDeleted, NORMAL)
                .last("LIMIT 1 FOR UPDATE"), false);
        if (Objects.nonNull(pending)) {
            throw this.badRequest("当前题型已有待发布版本");
        }
        TestVersionEntity before = this.copyVersion(target);
        target.setVersionStatus(PENDING);
        target.setScheduledAt(scheduledAt);
        this.versionMapper.updateById(target);
        this.writeAudit(BIZ_VERSION, versionId, ACTION_SCHEDULE, before, target,
                operatorId, "计划发布时间：" + scheduledAt);
        return this.getVersion(versionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestVersionResponse cancelSchedule(Long versionId, Long operatorId) {
        this.lockTestForVersion(versionId);
        TestVersionEntity target = this.selectVersionForUpdate(versionId);
        if (Objects.isNull(target)) {
            throw this.notFound("题型版本不存在");
        }
        if (PENDING != target.getVersionStatus()) {
            throw this.badRequest("只有待发布版本可以取消排期");
        }
        TestVersionEntity before = this.copyVersion(target);
        target.setVersionStatus(DRAFT);
        target.setScheduledAt(null);
        this.versionMapper.updateById(target);
        this.writeAudit(BIZ_VERSION, versionId, ACTION_CANCEL_SCHEDULE, before, target,
                operatorId, "取消发布排期");
        return this.getVersion(versionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TestVersionResponse publish(Long versionId, Long operatorId) {
        this.lockTestForVersion(versionId);
        TestVersionEntity target = this.selectVersionForUpdate(versionId);
        if (Objects.isNull(target)) {
            throw this.notFound("题型版本不存在");
        }
        if (PUBLISHED == target.getVersionStatus()) {
            return this.getVersion(versionId);
        }
        if (DRAFT != target.getVersionStatus() && PENDING != target.getVersionStatus()) {
            throw this.badRequest("只有草稿或待发布版本可以发布");
        }
        PublishCheckResponse check = this.validatePublish(target);
        if (!check.passed()) {
            throw this.badRequest(String.join("；", check.errors()));
        }
        TestVersionEntity current = this.selectPublishedVersionForUpdate(target.getTestId());
        LocalDateTime now = LocalDateTime.now();
        if (Objects.nonNull(current) && !Objects.equals(current.getId(), versionId)) {
            TestVersionEntity previous = this.copyVersion(current);
            current.setVersionStatus(OFFLINE);
            current.setOfflineAt(now);
            this.versionMapper.updateById(current);
            this.writeAudit(BIZ_VERSION, current.getId(), ACTION_OFFLINE, previous, current,
                    operatorId, "被新版本 " + versionId + " 替换");
        }
        TestVersionEntity before = this.copyVersion(target);
        target.setVersionStatus(PUBLISHED);
        target.setPublishedAt(now);
        target.setOfflineAt(null);
        target.setScheduledAt(null);
        this.versionMapper.updateById(target);
        this.writeAudit(BIZ_VERSION, versionId, ACTION_PUBLISH, before, target, operatorId, null);
        return this.getVersion(versionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestVersionResponse offline(Long versionId, String reason, Long operatorId) {
        this.lockTestForVersion(versionId);
        TestVersionEntity version = this.selectVersionForUpdate(versionId);
        if (Objects.isNull(version)) {
            throw this.notFound("题型版本不存在");
        }
        if (PUBLISHED != version.getVersionStatus()) {
            throw this.badRequest("只有已发布版本可以下线");
        }
        TestVersionEntity before = this.copyVersion(version);
        version.setVersionStatus(OFFLINE);
        version.setOfflineAt(LocalDateTime.now());
        this.versionMapper.updateById(version);
        this.writeAudit(BIZ_VERSION, versionId, ACTION_OFFLINE, before, version, operatorId, reason);
        return this.getVersion(versionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestVersionResponse archive(Long versionId, Long operatorId) {
        this.lockTestForVersion(versionId);
        TestVersionEntity version = this.selectVersionForUpdate(versionId);
        if (Objects.isNull(version)) {
            throw this.notFound("题型版本不存在");
        }
        if (OFFLINE != version.getVersionStatus()) {
            throw this.badRequest("只有已下线版本可以归档");
        }
        TestVersionEntity before = this.copyVersion(version);
        version.setVersionStatus(ARCHIVED);
        this.versionMapper.updateById(version);
        this.writeAudit(BIZ_VERSION, versionId, ACTION_ARCHIVE, before, version,
                operatorId, "归档版本");
        return this.getVersion(versionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public int publishDueVersions() {
        LocalDateTime now = LocalDateTime.now();
        // 先锁题型再锁版本，与立即发布、类型修改保持同一顺序；停用/删除后的排期不生效。
        List<TestVersionEntity> candidates = this.versionMapper.selectList(
                Wrappers.<TestVersionEntity>lambdaQuery()
                        .eq(TestVersionEntity::getVersionStatus, PENDING)
                        .le(TestVersionEntity::getScheduledAt, now)
                        .eq(TestVersionEntity::getDeleted, NORMAL));
        if (candidates.isEmpty()) {
            return 0;
        }
        Set<Long> candidateTestIds = candidates.stream().map(TestVersionEntity::getTestId).collect(Collectors.toSet());
        List<TestEntity> tests = this.list(Wrappers.<TestEntity>lambdaQuery()
                .in(TestEntity::getId, candidateTestIds)
                .eq(TestEntity::getDeleted, NORMAL)
                .orderByAsc(TestEntity::getId)
                .last("FOR UPDATE"));
        if (tests.isEmpty()) {
            return 0;
        }
        Set<Long> activeCategories = this.categoryService.listByIds(tests.stream()
                        .map(TestEntity::getCategoryId).collect(Collectors.toSet())).stream()
                .filter(category -> ENABLED == category.getStatus())
                .map(CategoryEntity::getId).collect(Collectors.toSet());
        List<Long> eligibleTestIds = tests.stream()
                .filter(test -> ENABLED == test.getStatus() && activeCategories.contains(test.getCategoryId()))
                .map(TestEntity::getId).toList();
        if (eligibleTestIds.isEmpty()) {
            return 0;
        }
        List<TestVersionEntity> dueVersions = this.versionMapper.selectList(
                Wrappers.<TestVersionEntity>lambdaQuery()
                        .in(TestVersionEntity::getTestId, eligibleTestIds)
                        .eq(TestVersionEntity::getVersionStatus, PENDING)
                        .le(TestVersionEntity::getScheduledAt, now)
                        .eq(TestVersionEntity::getDeleted, NORMAL)
                        .orderByAsc(TestVersionEntity::getScheduledAt)
                        .last("FOR UPDATE"));
        if (dueVersions.isEmpty()) {
            return 0;
        }
        Map<Long, TestVersionEntity> beforeVersions = dueVersions.stream()
                .collect(Collectors.toMap(TestVersionEntity::getId, this::copyVersion));
        Set<Long> testIds = dueVersions.stream().map(TestVersionEntity::getTestId).collect(Collectors.toSet());
        List<TestVersionEntity> currentVersions = this.versionMapper.selectList(
                Wrappers.<TestVersionEntity>lambdaQuery()
                        .in(TestVersionEntity::getTestId, testIds)
                        .eq(TestVersionEntity::getVersionStatus, PUBLISHED)
                        .eq(TestVersionEntity::getDeleted, NORMAL)
                        .last("FOR UPDATE"));
        List<TestVersionEntity> previousVersions = currentVersions.stream().map(this::copyVersion).toList();
        currentVersions.forEach(version -> {
            version.setVersionStatus(OFFLINE);
            version.setOfflineAt(now);
        });
        if (!currentVersions.isEmpty()) {
            this.versionMapper.updateById(currentVersions);
        }
        dueVersions.forEach(version -> {
            version.setVersionStatus(PUBLISHED);
            version.setPublishedAt(now);
            version.setOfflineAt(null);
            version.setScheduledAt(null);
        });
        this.versionMapper.updateById(dueVersions);
        List<ContentAuditLogEntity> audits = new ArrayList<>(dueVersions.stream()
                .map(version -> this.buildAudit(BIZ_VERSION, version.getId(), ACTION_PUBLISH,
                        beforeVersions.get(version.getId()), version, SYSTEM_OPERATOR_ID, "排期自动发布"))
                .toList());
        for (int index = 0; index < currentVersions.size(); index++) {
            TestVersionEntity current = currentVersions.get(index);
            audits.add(this.buildAudit(BIZ_VERSION, current.getId(), ACTION_OFFLINE,
                    previousVersions.get(index), current, SYSTEM_OPERATOR_ID, "被排期新版本替换"));
        }
        this.auditMapper.insert(audits);
        return dueVersions.size();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> pageAudits(AuditLogQuery query) {
        long total = this.auditMapper.selectCount(this.auditQuery(query));
        long offset = (query.getPage() - 1) * query.getSize();
        List<AuditLogResponse> records = this.auditMapper.selectList(this.auditQuery(query)
                        .orderByDesc(ContentAuditLogEntity::getCreateDate)
                        .orderByDesc(ContentAuditLogEntity::getId)
                        .last("LIMIT " + offset + ", " + query.getSize()))
                .stream().map(this::toAuditResponse).toList();
        return new PageResponse<>(records, total, query.getPage(), query.getSize());
    }

    private PublishCheckResponse validatePublish(TestVersionEntity version) {
        List<String> errors = new ArrayList<>();
        TestEntity test = this.getTestEntity(version.getTestId());
        if (ENABLED != test.getStatus()) {
            errors.add("题型已停用");
        }
        CategoryEntity category = this.categoryService.getById(test.getCategoryId());
        if (Objects.isNull(category) || ENABLED != category.getStatus()) {
            errors.add("分类不存在或已停用");
        }
        List<ScoreDimensionEntity> dimensions = this.listDimensions(version.getId());
        List<QuestionEntity> questions = this.questionMapper.selectList(Wrappers.<QuestionEntity>lambdaQuery()
                .eq(QuestionEntity::getVersionId, version.getId()).eq(QuestionEntity::getDeleted, NORMAL));
        List<Long> questionIds = questions.stream().map(QuestionEntity::getId).toList();
        List<QuestionOptionEntity> options = questionIds.isEmpty() ? List.of() : this.optionMapper.selectList(
                Wrappers.<QuestionOptionEntity>lambdaQuery()
                        .in(QuestionOptionEntity::getQuestionId, questionIds)
                        .eq(QuestionOptionEntity::getDeleted, NORMAL));
        Map<Long, List<QuestionOptionEntity>> optionMap = options.stream()
                .collect(Collectors.groupingBy(QuestionOptionEntity::getQuestionId));
        Set<Long> dimensionIds = dimensions.stream().map(ScoreDimensionEntity::getId).collect(Collectors.toSet());
        if (dimensions.isEmpty()) {
            errors.add("至少配置一个计分维度");
        }
        if (version.getDrawQuestionCount() > questions.size()) {
            errors.add("单次抽题数不能超过可用题目数");
        }
        if (version.getDrawQuestionCount() < dimensions.size()) {
            errors.add("单次抽题数不足以覆盖全部计分维度");
        }
        for (QuestionEntity question : questions) {
            List<QuestionOptionEntity> questionOptions = optionMap.getOrDefault(question.getId(), List.of());
            if (questionOptions.size() < 2) {
                errors.add("第 " + question.getQuestionNo() + " 题至少需要两个选项");
                continue;
            }
            if (SINGLE == question.getQuestionType()) {
                if (!dimensionIds.contains(question.getDimensionId())) {
                    errors.add("第 " + question.getQuestionNo() + " 题未绑定有效维度");
                }
                if (question.getMinSelectCount() != 1 || question.getMaxSelectCount() != 1) {
                    errors.add("第 " + question.getQuestionNo() + " 题单选数量必须为 1");
                }
            } else if (MULTIPLE == question.getQuestionType()) {
                if (question.getMinSelectCount() != 2 || question.getMaxSelectCount() != questionOptions.size()) {
                    errors.add("第 " + question.getQuestionNo() + " 题多选题必须最少选择 2 项且最多全选");
                }
                if (questionOptions.stream().anyMatch(option -> !dimensionIds.contains(option.getDimensionId()))) {
                    errors.add("第 " + question.getQuestionNo() + " 题存在未绑定有效维度的选项");
                }
            } else {
                errors.add("第 " + question.getQuestionNo() + " 题类型无效");
            }
        }
        this.validateDimensionScoreRanges(dimensions, questions, optionMap, errors);
        List<ResultTemplateEntity> results = this.resultMapper.selectList(
                Wrappers.<ResultTemplateEntity>lambdaQuery()
                        .eq(ResultTemplateEntity::getVersionId, version.getId())
                        .eq(ResultTemplateEntity::getDeleted, NORMAL));
        try {
            this.validateResultCoverage(dimensions, results);
        } catch (BusinessException exception) {
            errors.add(exception.getMessage());
        }
        return new PublishCheckResponse(errors.isEmpty(), List.copyOf(errors));
    }

    private void validateDimensionScoreRanges(List<ScoreDimensionEntity> dimensions,
                                              List<QuestionEntity> questions,
                                              Map<Long, List<QuestionOptionEntity>> optionMap,
                                              List<String> errors) {
        for (ScoreDimensionEntity dimension : dimensions) {
            int minTotal = 0;
            int maxTotal = 0;
            boolean represented = false;
            for (QuestionEntity question : questions) {
                List<QuestionOptionEntity> options = optionMap.getOrDefault(question.getId(), List.of());
                if (SINGLE == question.getQuestionType() && Objects.equals(question.getDimensionId(), dimension.getId())) {
                    represented = true;
                    int min = options.stream().mapToInt(QuestionOptionEntity::getScoreValue).min().orElse(0);
                    int max = options.stream().mapToInt(QuestionOptionEntity::getScoreValue).max().orElse(0);
                    minTotal += question.getRequiredFlag() == 1 ? min : Math.min(0, min);
                    maxTotal += question.getRequiredFlag() == 1 ? max : Math.max(0, max);
                } else if (MULTIPLE == question.getQuestionType()) {
                    List<Integer> values = options.stream()
                            .filter(option -> Objects.equals(option.getDimensionId(), dimension.getId()))
                            .map(QuestionOptionEntity::getScoreValue).sorted().toList();
                    if (!values.isEmpty()) {
                        represented = true;
                    }
                    int[] range = this.multipleContributionRange(values, options.size() - values.size(),
                            question.getMinSelectCount(), question.getMaxSelectCount(), question.getRequiredFlag() == 0);
                    minTotal += range[0];
                    maxTotal += range[1];
                }
            }
            if (!represented) {
                errors.add("维度“" + dimension.getDimensionName() + "”没有可计分题");
            } else if (maxTotal <= minTotal) {
                errors.add("维度“" + dimension.getDimensionName() + "”理论最高分必须大于最低分");
            }
        }
    }

    private int[] multipleContributionRange(List<Integer> sortedValues, int otherCount,
                                            int minSelect, int maxSelect, boolean optional) {
        int min = optional ? 0 : Integer.MAX_VALUE;
        int max = optional ? 0 : Integer.MIN_VALUE;
        for (int selected = minSelect; selected <= maxSelect; selected++) {
            int minDimensionSelected = Math.max(0, selected - otherCount);
            int maxDimensionSelected = Math.min(selected, sortedValues.size());
            for (int count = minDimensionSelected; count <= maxDimensionSelected; count++) {
                int low = sortedValues.subList(0, count).stream().mapToInt(Integer::intValue).sum();
                int high = sortedValues.subList(sortedValues.size() - count, sortedValues.size())
                        .stream().mapToInt(Integer::intValue).sum();
                min = Math.min(min, low);
                max = Math.max(max, high);
            }
        }
        return new int[]{min == Integer.MAX_VALUE ? 0 : min, max == Integer.MIN_VALUE ? 0 : max};
    }

    private void validateQuestion(Long versionId, Long questionId, QuestionSaveRequest request) {
        long duplicate = this.questionMapper.selectCount(Wrappers.<QuestionEntity>lambdaQuery()
                .eq(QuestionEntity::getVersionId, versionId)
                .eq(QuestionEntity::getQuestionNo, request.questionNo())
                .ne(Objects.nonNull(questionId), QuestionEntity::getId, questionId)
                .eq(QuestionEntity::getDeleted, NORMAL));
        if (duplicate > 0) {
            throw this.badRequest("题号已存在");
        }
        Set<Long> dimensionIds = this.listDimensions(versionId).stream()
                .map(ScoreDimensionEntity::getId).collect(Collectors.toSet());
        this.validateQuestionContent(request, dimensionIds);
    }

    private void validateQuestionContent(QuestionSaveRequest request, Set<Long> dimensionIds) {
        if (SINGLE == request.questionType()) {
            if (!dimensionIds.contains(request.dimensionId())
                    || request.minSelectCount() != 1 || request.maxSelectCount() != 1) {
                throw this.badRequest("单选题必须绑定有效维度，且选择数量固定为 1");
            }
        } else if (MULTIPLE == request.questionType()) {
            if (request.minSelectCount() != 2
                    || request.maxSelectCount() != request.options().size()
                    || request.options().stream().anyMatch(option -> !dimensionIds.contains(option.dimensionId()))) {
                throw this.badRequest("多选题必须最少选择 2 项且最多全选，并绑定有效选项维度");
            }
        } else {
            throw this.badRequest("题目类型无效");
        }
        Set<String> codes = new HashSet<>();
        for (int index = 0; index < request.options().size(); index++) {
            String code = this.optionCode(request.options().get(index), index);
            if (!codes.add(code)) {
                throw this.badRequest("选项编码不能重复");
            }
        }
    }

    private List<ResultTemplateEntity> buildResults(Long versionId,
                                                    List<ScoreDimensionEntity> dimensions,
                                                    List<ResultTemplateSaveRequest> requests) {
        Set<Long> dimensionIds = dimensions.stream().map(ScoreDimensionEntity::getId).collect(Collectors.toSet());
        Set<String> resultCodes = new HashSet<>();
        List<ResultTemplateEntity> results = new ArrayList<>(requests.size());
        for (int index = 0; index < requests.size(); index++) {
            ResultTemplateSaveRequest request = requests.get(index);
            if (!dimensionIds.contains(request.dimensionId())) {
                throw this.badRequest("结果模板引用了无效计分维度");
            }
            String resultCode = this.hasText(request.resultCode()) ? request.resultCode() : "R" + this.randomCode();
            if (!resultCodes.add(resultCode)) {
                throw this.badRequest("结果编码不能重复");
            }
            ResultTemplateEntity entity = new ResultTemplateEntity();
            entity.setVersionId(versionId);
            entity.setDimensionId(request.dimensionId());
            entity.setResultCode(resultCode);
            entity.setResultName(request.resultName().trim());
            entity.setScoreMin(request.scoreMin());
            entity.setScoreMax(request.scoreMax());
            entity.setBasicResultJson(request.basicResultJson().toString());
            entity.setDeepResultJson(Objects.isNull(request.deepResultJson()) ? null : request.deepResultJson().toString());
            entity.setShareCopyJson(Objects.isNull(request.shareCopyJson()) ? null : request.shareCopyJson().toString());
            entity.setSortNo(Objects.isNull(request.sortNo()) ? index : request.sortNo());
            entity.setDeleted(NORMAL);
            results.add(entity);
        }
        return results;
    }

    private void validateResultCoverage(List<ScoreDimensionEntity> dimensions,
                                        List<ResultTemplateEntity> templates) {
        Map<Long, List<ResultTemplateEntity>> resultMap = templates.stream()
                .collect(Collectors.groupingBy(ResultTemplateEntity::getDimensionId));
        for (ScoreDimensionEntity dimension : dimensions) {
            List<ResultTemplateEntity> ranges = new ArrayList<>(resultMap.getOrDefault(dimension.getId(), List.of()));
            ranges.sort(Comparator.comparing(ResultTemplateEntity::getScoreMin));
            if (ranges.isEmpty() || ranges.get(0).getScoreMin().compareTo(ZERO) != 0
                    || ranges.get(ranges.size() - 1).getScoreMax().compareTo(HUNDRED) != 0) {
                throw this.badRequest("维度“" + dimension.getDimensionName() + "”的结果区间未覆盖 0-100");
            }
            for (int index = 0; index < ranges.size(); index++) {
                ResultTemplateEntity current = ranges.get(index);
                if (current.getScoreMin().compareTo(current.getScoreMax()) >= 0
                        || (index > 0 && current.getScoreMin().compareTo(ranges.get(index - 1).getScoreMax()) != 0)) {
                    throw this.badRequest("维度“" + dimension.getDimensionName() + "”的结果区间存在断档或重叠");
                }
            }
        }
        if (!resultMap.keySet().equals(dimensions.stream().map(ScoreDimensionEntity::getId).collect(Collectors.toSet()))) {
            throw this.badRequest("结果模板包含无效计分维度");
        }
    }

    private List<ScoreDimensionEntity> buildDimensions(Long versionId,
                                                       List<ScoreDimensionSaveRequest> requests,
                                                       Map<Long, ScoreDimensionEntity> existingMap) {
        Set<String> codes = new HashSet<>();
        List<ScoreDimensionEntity> dimensions = new ArrayList<>(requests.size());
        for (ScoreDimensionSaveRequest request : requests) {
            ScoreDimensionEntity existing = Objects.isNull(request.id()) ? null : existingMap.get(request.id());
            if (Objects.nonNull(request.id()) && Objects.isNull(existing)) {
                throw this.badRequest("计分维度不存在");
            }
            String code = this.hasText(request.dimensionCode()) ? request.dimensionCode()
                    : Objects.nonNull(existing) ? existing.getDimensionCode() : "D" + this.randomCode();
            if (!codes.add(code)) {
                throw this.badRequest("维度编码不能重复");
            }
            ScoreDimensionEntity entity = new ScoreDimensionEntity();
            entity.setId(request.id());
            entity.setVersionId(versionId);
            entity.setDimensionCode(code);
            entity.setDimensionName(request.dimensionName().trim());
            entity.setSortNo(request.sortNo());
            entity.setDeleted(NORMAL);
            dimensions.add(entity);
        }
        return dimensions;
    }

    private QuestionEntity buildQuestion(Long versionId, Long questionId, QuestionSaveRequest request) {
        QuestionEntity entity = new QuestionEntity();
        entity.setId(questionId);
        entity.setVersionId(versionId);
        entity.setQuestionType(request.questionType());
        entity.setDimensionId(SINGLE == request.questionType() ? request.dimensionId() : null);
        entity.setMinSelectCount(request.minSelectCount());
        entity.setMaxSelectCount(request.maxSelectCount());
        entity.setQuestionNo(request.questionNo());
        entity.setQuestionText(request.questionText().trim());
        entity.setRequiredFlag(request.requiredFlag());
        entity.setSortNo(Objects.isNull(request.sortNo()) ? request.questionNo() : request.sortNo());
        entity.setDeleted(NORMAL);
        return entity;
    }

    private List<QuestionOptionEntity> buildOptions(Long questionId, QuestionSaveRequest request) {
        List<QuestionOptionEntity> options = new ArrayList<>(request.options().size());
        for (int index = 0; index < request.options().size(); index++) {
            QuestionOptionSaveRequest item = request.options().get(index);
            QuestionOptionEntity option = new QuestionOptionEntity();
            option.setQuestionId(questionId);
            option.setOptionCode(this.optionCode(item, index));
            option.setOptionText(item.optionText().trim());
            option.setDimensionId(MULTIPLE == request.questionType() ? item.dimensionId() : null);
            option.setScoreValue(item.scoreValue());
            option.setSortNo(Objects.isNull(item.sortNo()) ? request.options().size() - index : item.sortNo());
            option.setDeleted(NORMAL);
            options.add(option);
        }
        return options;
    }

    private QuestionResponse getQuestion(Long questionId) {
        QuestionEntity question = this.getQuestionEntity(questionId);
        List<QuestionOptionEntity> options = this.optionMapper.selectList(
                Wrappers.<QuestionOptionEntity>lambdaQuery()
                        .eq(QuestionOptionEntity::getQuestionId, questionId)
                        .eq(QuestionOptionEntity::getDeleted, NORMAL)
                        .orderByDesc(QuestionOptionEntity::getSortNo));
        return this.toQuestionResponse(question, options);
    }

    private QuestionResponse toQuestionResponse(QuestionEntity question, List<QuestionOptionEntity> options) {
        return new QuestionResponse(question.getId(), question.getVersionId(), question.getQuestionType(),
                question.getDimensionId(), question.getMinSelectCount(), question.getMaxSelectCount(),
                question.getQuestionNo(), question.getQuestionText(), question.getRequiredFlag(),
                question.getSortNo(), options.stream().map(option -> new QuestionOptionResponse(
                        option.getId(), option.getOptionCode(), option.getOptionText(), option.getDimensionId(),
                        option.getScoreValue(), option.getSortNo())).toList());
    }

    private TestVersionResponse toVersionResponse(TestVersionEntity version,
                                                  List<ScoreDimensionEntity> dimensions,
                                                  long questionCount) {
        return new TestVersionResponse(version.getId(), version.getTestId(), version.getVersionNo(),
                version.getTitle(), version.getCoverUrl(), version.getDetailImageUrl(),
                version.getDescription(), version.getEstimatedMinutes(),
                version.getDrawQuestionCount(), version.getVersionStatus(), version.getVersionNote(),
                version.getScheduledAt(), version.getPublishedAt(), version.getOfflineAt(), questionCount,
                dimensions.stream().map(this::toDimensionResponse).toList());
    }

    private ScoreDimensionResponse toDimensionResponse(ScoreDimensionEntity dimension) {
        return new ScoreDimensionResponse(dimension.getId(), dimension.getVersionId(),
                dimension.getDimensionCode(), dimension.getDimensionName(), dimension.getSortNo());
    }

    private ResultTemplateResponse toResultResponse(ResultTemplateEntity entity) {
        return new ResultTemplateResponse(entity.getId(), entity.getDimensionId(), entity.getResultCode(),
                entity.getResultName(), entity.getScoreMin(), entity.getScoreMax(),
                this.readJson(entity.getBasicResultJson()), this.readJson(entity.getDeepResultJson()),
                this.readJson(entity.getShareCopyJson()), entity.getSortNo());
    }

    private AuditLogResponse toAuditResponse(ContentAuditLogEntity entity) {
        return new AuditLogResponse(entity.getId(), entity.getBizType(), entity.getBizId(), entity.getActionType(),
                entity.getBeforeSnapshot(), entity.getAfterSnapshot(), entity.getReason(),
                entity.getOperatorId(), entity.getCreateDate());
    }

    private void applyTest(TestEntity entity, TestSaveRequest request) {
        entity.setTestName(request.testName().trim());
        entity.setTestType(request.testType());
        entity.setCategoryId(request.categoryId());
        entity.setStatus(request.status());
    }

    private void applyVersion(TestVersionEntity version, TestVersionSaveRequest request) {
        version.setTitle(request.title().trim());
        version.setCoverUrl(request.coverUrl().trim());
        version.setDetailImageUrl(Objects.isNull(request.detailImageUrl()) || request.detailImageUrl().isBlank()
                ? null : request.detailImageUrl().trim());
        version.setDescription(request.description());
        version.setEstimatedMinutes(request.estimatedMinutes());
        version.setDrawQuestionCount(request.drawQuestionCount());
        version.setVersionNote(request.versionNote());
    }

    private void requireActiveCategory(Long categoryId) {
        CategoryEntity category = this.categoryService.getById(categoryId);
        if (Objects.isNull(category) || ENABLED != category.getStatus()) {
            throw this.badRequest("分类不存在或已停用");
        }
    }

    private TestEntity getTestEntity(Long id) {
        TestEntity entity = this.getById(id);
        if (Objects.isNull(entity)) {
            throw this.notFound("题型不存在");
        }
        return entity;
    }

    private TestVersionEntity getVersionEntity(Long id) {
        TestVersionEntity version = this.versionMapper.selectById(id);
        if (Objects.isNull(version)) {
            throw this.notFound("题型版本不存在");
        }
        return version;
    }

    private TestVersionEntity requireDraftVersionForUpdate(Long id) {
        TestVersionEntity version = this.selectVersionForUpdate(id);
        if (Objects.isNull(version)) {
            throw this.notFound("题型版本不存在");
        }
        if (DRAFT != version.getVersionStatus()) {
            throw new BusinessException(HttpStatus.CONFLICT, HttpStatus.CONFLICT.value(),
                    "只有草稿版本可以修改；已发布内容请创建新版本");
        }
        return version;
    }

    private TestEntity selectTestForUpdate(Long id) {
        return this.lambdaQuery()
                .eq(TestEntity::getId, id)
                .eq(TestEntity::getDeleted, NORMAL)
                .last("FOR UPDATE")
                .one();
    }

    private void lockTestForVersion(Long versionId) {
        TestVersionEntity snapshot = this.getVersionEntity(versionId);
        if (Objects.isNull(this.selectTestForUpdate(snapshot.getTestId()))) {
            throw this.notFound("题型不存在");
        }
    }

    private TestVersionEntity selectVersionForUpdate(Long id) {
        return this.versionMapper.selectOne(Wrappers.<TestVersionEntity>lambdaQuery()
                .eq(TestVersionEntity::getId, id)
                .eq(TestVersionEntity::getDeleted, NORMAL)
                .last("FOR UPDATE"), false);
    }

    private TestVersionEntity selectPublishedVersionForUpdate(Long testId) {
        return this.versionMapper.selectOne(Wrappers.<TestVersionEntity>lambdaQuery()
                .eq(TestVersionEntity::getTestId, testId)
                .eq(TestVersionEntity::getVersionStatus, PUBLISHED)
                .eq(TestVersionEntity::getDeleted, NORMAL)
                .orderByDesc(TestVersionEntity::getVersionNo)
                .last("LIMIT 1 FOR UPDATE"), false);
    }

    private Integer selectMaxVersionNo(Long testId) {
        TestVersionEntity version = this.versionMapper.selectOne(Wrappers.<TestVersionEntity>lambdaQuery()
                .select(TestVersionEntity::getVersionNo)
                .eq(TestVersionEntity::getTestId, testId)
                .eq(TestVersionEntity::getDeleted, NORMAL)
                .orderByDesc(TestVersionEntity::getVersionNo)
                .last("LIMIT 1 FOR UPDATE"), false);
        return Objects.isNull(version) ? null : version.getVersionNo();
    }

    private LambdaQueryWrapper<ContentAuditLogEntity> auditQuery(AuditLogQuery query) {
        return Wrappers.<ContentAuditLogEntity>lambdaQuery()
                .eq(Objects.nonNull(query.getBizType()), ContentAuditLogEntity::getBizType, query.getBizType())
                .eq(Objects.nonNull(query.getBizId()), ContentAuditLogEntity::getBizId, query.getBizId())
                .eq(Objects.nonNull(query.getOperatorId()),
                        ContentAuditLogEntity::getOperatorId, query.getOperatorId());
    }

    private QuestionEntity getQuestionEntity(Long id) {
        QuestionEntity entity = this.questionMapper.selectById(id);
        if (Objects.isNull(entity)) {
            throw this.notFound("题目不存在");
        }
        return entity;
    }

    private List<ScoreDimensionEntity> listDimensions(Long versionId) {
        return this.dimensionMapper.selectList(Wrappers.<ScoreDimensionEntity>lambdaQuery()
                .eq(ScoreDimensionEntity::getVersionId, versionId)
                .eq(ScoreDimensionEntity::getDeleted, NORMAL)
                .orderByAsc(ScoreDimensionEntity::getSortNo));
    }

    private TestVersionEntity copyVersion(TestVersionEntity source) {
        TestVersionEntity copy = new TestVersionEntity();
        copy.setId(source.getId());
        copy.setTestId(source.getTestId());
        copy.setVersionNo(source.getVersionNo());
        copy.setDetailImageUrl(source.getDetailImageUrl());
        copy.setVersionStatus(source.getVersionStatus());
        copy.setScheduledAt(source.getScheduledAt());
        copy.setPublishedAt(source.getPublishedAt());
        copy.setOfflineAt(source.getOfflineAt());
        return copy;
    }

    private void writeAudit(Integer bizType, Long bizId, Integer actionType,
                            Object before, Object after, Long operatorId, String reason) {
        this.auditMapper.insert(this.buildAudit(bizType, bizId, actionType, before, after, operatorId, reason));
    }

    private ContentAuditLogEntity buildAudit(Integer bizType, Long bizId, Integer actionType,
                                              Object before, Object after, Long operatorId, String reason) {
        ContentAuditLogEntity audit = new ContentAuditLogEntity();
        audit.setBizType(bizType);
        audit.setBizId(bizId);
        audit.setActionType(actionType);
        audit.setBeforeSnapshot(Objects.isNull(before) ? null : this.objectMapper.valueToTree(before).toString());
        audit.setAfterSnapshot(Objects.isNull(after) ? null : this.objectMapper.valueToTree(after).toString());
        audit.setReason(reason);
        audit.setOperatorId(operatorId);
        return audit;
    }

    private JsonNode readJson(String json) {
        if (!this.hasText(json)) {
            return null;
        }
        try {
            return this.objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库结果 JSON 无法解析", exception);
        }
    }

    private String optionCode(QuestionOptionSaveRequest request, int index) {
        return this.hasText(request.optionCode()) ? request.optionCode().trim() : String.valueOf((char) ('A' + index));
    }

    private String randomCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    private boolean hasText(String value) {
        return Objects.nonNull(value) && !value.isBlank();
    }

    private BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.value(), message);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.value(), message);
    }
}
