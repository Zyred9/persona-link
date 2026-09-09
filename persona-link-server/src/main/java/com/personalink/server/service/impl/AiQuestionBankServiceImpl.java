package com.personalink.server.service.impl;

import lombok.RequiredArgsConstructor;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalink.server.ai.AiQuestionGenerationWorker;
import com.personalink.server.ai.DeepSeekClient;
import com.personalink.server.dto.AiGenerationStartRequest;
import com.personalink.server.dto.AiGenerationTaskResponse;
import com.personalink.server.dto.AiGenerationTaskQuery;
import com.personalink.server.dto.AiGenerationTaskSummaryResponse;
import com.personalink.server.dto.PageResponse;
import com.personalink.server.dto.PublishCheckResponse;
import com.personalink.server.dto.TestResponse;
import com.personalink.server.dto.TestSaveRequest;
import com.personalink.server.dto.TestVersionResponse;
import com.personalink.server.dto.TestVersionSaveRequest;
import com.personalink.server.entity.AiGenerationTaskEntity;
import com.personalink.server.entity.TestEntity;
import com.personalink.server.entity.TestVersionEntity;
import com.personalink.server.enums.AiGenerationTaskStatus;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.AiGenerationTaskMapper;
import com.personalink.server.mapper.TestMapper;
import com.personalink.server.mapper.TestVersionMapper;
import com.personalink.server.service.AiQuestionBankService;
import com.personalink.server.service.ContentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** AI 题库生成任务业务实现。 */
@Service
@RequiredArgsConstructor
public class AiQuestionBankServiceImpl implements AiQuestionBankService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiQuestionBankServiceImpl.class);
    private static final int NORMAL = 0;
    private static final int ENABLED = 1;
    private static final int BATCH_SIZE = 10;

    private final AiGenerationTaskMapper taskMapper;
    private final ContentService contentService;
    private final DeepSeekClient deepSeekClient;
    private final AiQuestionGenerationWorker generationWorker;
    private final TransactionTemplate transactionTemplate;
    private final TestVersionMapper testVersionMapper;
    private final TestMapper testMapper;

    @Override
    public AiGenerationTaskResponse create(AiGenerationStartRequest request, Long operatorId) {
        AiGenerationTaskEntity existing = this.findByRequestId(request.requestId());
        if (Objects.nonNull(existing)) {
            this.dispatchIfPending(existing);
            return this.toResponse(existing);
        }
        if (request.drawQuestionCount() > request.targetQuestionCount()) {
            throw this.badRequest("单次抽题数不能超过目标题库题目数");
        }
        this.deepSeekClient.requireConfigured();
        Long taskId;
        try {
            taskId = this.transactionTemplate.execute(status -> this.createTask(request, operatorId));
        } catch (DuplicateKeyException exception) {
            existing = this.findByRequestId(request.requestId());
            if (Objects.isNull(existing)) {
                throw exception;
            }
            taskId = existing.getId();
        }
        this.dispatch(taskId);
        return this.get(taskId);
    }

    @Override
    public PageResponse<AiGenerationTaskSummaryResponse> page(AiGenerationTaskQuery query) {
        boolean hasKeyword = Objects.nonNull(query.getKeyword()) && !query.getKeyword().isBlank();
        long total = this.taskMapper.selectCount(Wrappers.<AiGenerationTaskEntity>lambdaQuery()
                .like(hasKeyword, AiGenerationTaskEntity::getTaskNo, hasKeyword ? query.getKeyword().trim() : null)
                .eq(Objects.nonNull(query.getTaskStatus()), AiGenerationTaskEntity::getTaskStatus, query.getTaskStatus())
                .eq(AiGenerationTaskEntity::getDeleted, NORMAL));
        long offset = (query.getPage() - 1) * query.getSize();
        List<AiGenerationTaskEntity> tasks = this.taskMapper.selectList(
                Wrappers.<AiGenerationTaskEntity>lambdaQuery()
                        .like(hasKeyword, AiGenerationTaskEntity::getTaskNo, hasKeyword ? query.getKeyword().trim() : null)
                        .eq(Objects.nonNull(query.getTaskStatus()), AiGenerationTaskEntity::getTaskStatus, query.getTaskStatus())
                        .eq(AiGenerationTaskEntity::getDeleted, NORMAL)
                        .orderByDesc(AiGenerationTaskEntity::getCreateDate)
                        .last("LIMIT " + offset + "," + query.getSize()));
        if (tasks.isEmpty()) {
            return new PageResponse<>(List.of(), total, query.getPage(), query.getSize());
        }
        Map<Long, TestVersionEntity> versionMap = this.testVersionMapper.selectByIds(tasks.stream()
                        .map(AiGenerationTaskEntity::getVersionId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(TestVersionEntity::getId, Function.identity(), (first, second) -> first));
        Map<Long, TestEntity> testMap = versionMap.isEmpty() ? Map.of() : this.testMapper.selectByIds(
                        versionMap.values().stream().map(TestVersionEntity::getTestId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(TestEntity::getId, Function.identity(), (first, second) -> first));
        List<AiGenerationTaskSummaryResponse> records = tasks.stream().map(task -> {
            TestVersionEntity version = versionMap.get(task.getVersionId());
            TestEntity test = Objects.isNull(version) ? null : testMap.get(version.getTestId());
            return new AiGenerationTaskSummaryResponse(task.getId(), task.getTaskNo(),
                    Objects.isNull(test) ? "题型已删除" : test.getTestName(), task.getTargetQuestionCount(),
                    task.getGeneratedQuestionCount(), task.getTaskStatus(), task.getErrorMessage(),
                    task.getOperatorId(), task.getCreateDate(), task.getCompletedAt(), task.getSubmittedAt());
        }).toList();
        return new PageResponse<>(records, total, query.getPage(), query.getSize());
    }

    @Override
    public AiGenerationTaskResponse get(Long taskId) {
        return this.toResponse(this.requireTask(taskId));
    }

    @Override
    public AiGenerationTaskResponse retry(Long taskId) {
        this.deepSeekClient.requireConfigured();
        this.transactionTemplate.executeWithoutResult(status -> {
            AiGenerationTaskEntity task = this.requireTaskForUpdate(taskId);
            if (!Integer.valueOf(AiGenerationTaskStatus.FAILED.getCode()).equals(task.getTaskStatus())) {
                throw new BusinessException(HttpStatus.CONFLICT, HttpStatus.CONFLICT.value(),
                        "只有生成失败的任务可以重试");
            }
            this.taskMapper.update(null, Wrappers.<AiGenerationTaskEntity>lambdaUpdate()
                    .set(AiGenerationTaskEntity::getTaskStatus, AiGenerationTaskStatus.PENDING.getCode())
                    .set(AiGenerationTaskEntity::getRetryCount, task.getRetryCount() + 1)
                    .set(AiGenerationTaskEntity::getErrorMessage, null)
                    .eq(AiGenerationTaskEntity::getId, taskId)
                    .eq(AiGenerationTaskEntity::getTaskStatus, AiGenerationTaskStatus.FAILED.getCode())
                    .eq(AiGenerationTaskEntity::getDeleted, NORMAL));
        });
        this.dispatch(taskId);
        return this.get(taskId);
    }

    @Override
    public AiGenerationTaskResponse submit(Long taskId) {
        this.transactionTemplate.executeWithoutResult(status -> {
            AiGenerationTaskEntity task = this.requireTaskForUpdate(taskId);
            if (Integer.valueOf(AiGenerationTaskStatus.SUBMITTED.getCode()).equals(task.getTaskStatus())) {
                return;
            }
            if (!Integer.valueOf(AiGenerationTaskStatus.PENDING_REVIEW.getCode()).equals(task.getTaskStatus())) {
                throw new BusinessException(HttpStatus.CONFLICT, HttpStatus.CONFLICT.value(),
                        "只有待人工审核的任务可以提交题型库");
            }
            PublishCheckResponse check = this.contentService.checkPublish(task.getVersionId());
            if (!check.passed()) {
                throw this.badRequest(String.join("；", check.errors()));
            }
            AiGenerationTaskEntity update = new AiGenerationTaskEntity();
            update.setId(taskId);
            update.setTaskStatus(AiGenerationTaskStatus.SUBMITTED.getCode());
            update.setSubmittedAt(LocalDateTime.now());
            this.taskMapper.updateById(update);
        });
        return this.get(taskId);
    }

    /** 应用启动后将上次进程遗留的未完成任务标记为失败，等待人工重试。 */
    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedTasksFailed() {
        // ponytail: 当前按单实例部署回收中断任务；水平扩容时改为带实例标识和过期时间的任务租约。
        int interruptedCount = this.taskMapper.update(null, Wrappers.<AiGenerationTaskEntity>lambdaUpdate()
                .set(AiGenerationTaskEntity::getTaskStatus, AiGenerationTaskStatus.FAILED.getCode())
                .set(AiGenerationTaskEntity::getErrorMessage, "服务重启中断了生成，请手动重试")
                .in(AiGenerationTaskEntity::getTaskStatus,
                        AiGenerationTaskStatus.PENDING.getCode(), AiGenerationTaskStatus.GENERATING.getCode())
                .eq(AiGenerationTaskEntity::getDeleted, NORMAL));
        if (interruptedCount > 0) {
            LOGGER.warn("[AI题库] 服务重启中断了 {} 个未完成任务，已等待人工重试", interruptedCount);
        }
    }

    private Long createTask(AiGenerationStartRequest request, Long operatorId) {
        AiGenerationTaskEntity existing = this.findByRequestId(request.requestId());
        if (Objects.nonNull(existing)) {
            return existing.getId();
        }
        TestResponse test = this.contentService.createTest(new TestSaveRequest(
                request.testName(), request.testType(), request.categoryId(), ENABLED), operatorId);
        TestVersionResponse version = this.contentService.createVersion(test.getId(), new TestVersionSaveRequest(
                request.testName(), request.coverUrl(), request.detailImageUrl(),
                request.description(), request.estimatedMinutes(),
                request.drawQuestionCount(), "AI题库助手生成", List.of()), operatorId);
        AiGenerationTaskEntity task = new AiGenerationTaskEntity();
        task.setTaskNo("AI" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase());
        task.setRequestId(request.requestId().trim());
        task.setVersionId(version.id());
        task.setOperatorId(operatorId);
        task.setModelName(this.deepSeekClient.modelName());
        task.setPromptText(request.promptText().trim());
        task.setTargetQuestionCount(request.targetQuestionCount());
        task.setCurrentBatchNo(0);
        task.setTotalBatchCount((request.targetQuestionCount() + BATCH_SIZE - 1) / BATCH_SIZE);
        task.setCompletedBatchCount(0);
        task.setGeneratedQuestionCount(0);
        task.setRetryCount(0);
        task.setDimensionGeneratedFlag(0);
        task.setResultRuleGeneratedFlag(0);
        task.setTaskStatus(AiGenerationTaskStatus.PENDING.getCode());
        task.setDeleted(NORMAL);
        this.taskMapper.insert(task);
        return task.getId();
    }

    private AiGenerationTaskResponse toResponse(AiGenerationTaskEntity task) {
        TestVersionResponse version = this.contentService.getVersion(task.getVersionId());
        TestResponse test = this.contentService.getTest(version.testId());
        return new AiGenerationTaskResponse(task.getId(), task.getTaskNo(), version.testId(), task.getVersionId(),
                test.getTestName(), test.getTestType(), test.getCategoryId(), version.coverUrl(), version.detailImageUrl(),
                version.description(), version.estimatedMinutes(), version.drawQuestionCount(), task.getModelName(),
                task.getTargetQuestionCount(), Math.toIntExact(version.questionCount()), task.getCurrentBatchNo(),
                task.getTotalBatchCount(), task.getCompletedBatchCount(), task.getRetryCount(), task.getTaskStatus(),
                task.getErrorMessage(), task.getCompletedAt(), task.getSubmittedAt());
    }

    private AiGenerationTaskEntity findByRequestId(String requestId) {
        return this.taskMapper.selectOne(Wrappers.<AiGenerationTaskEntity>lambdaQuery()
                .eq(AiGenerationTaskEntity::getRequestId, requestId.trim())
                .eq(AiGenerationTaskEntity::getDeleted, NORMAL), false);
    }

    private AiGenerationTaskEntity requireTask(Long taskId) {
        AiGenerationTaskEntity task = this.taskMapper.selectById(taskId);
        if (Objects.isNull(task)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.value(),
                    "AI 生成任务不存在");
        }
        return task;
    }

    private AiGenerationTaskEntity requireTaskForUpdate(Long taskId) {
        AiGenerationTaskEntity task = this.taskMapper.selectOne(
                Wrappers.<AiGenerationTaskEntity>lambdaQuery()
                        .eq(AiGenerationTaskEntity::getId, taskId)
                        .eq(AiGenerationTaskEntity::getDeleted, NORMAL)
                        .last("FOR UPDATE"), false);
        if (Objects.isNull(task)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.value(),
                    "AI 生成任务不存在");
        }
        return task;
    }

    private void dispatchIfPending(AiGenerationTaskEntity task) {
        if (Integer.valueOf(AiGenerationTaskStatus.PENDING.getCode()).equals(task.getTaskStatus())
                && this.deepSeekClient.isConfigured()) {
            this.dispatch(task.getId());
        }
    }

    private void dispatch(Long taskId) {
        try {
            this.generationWorker.generateAsync(taskId);
        } catch (TaskRejectedException exception) {
            LOGGER.warn("[AI题库] 生成队列已满，任务转为失败等待重试，任务ID：{}", taskId);
            this.taskMapper.update(null, Wrappers.<AiGenerationTaskEntity>lambdaUpdate()
                    .set(AiGenerationTaskEntity::getTaskStatus, AiGenerationTaskStatus.FAILED.getCode())
                    .set(AiGenerationTaskEntity::getErrorMessage, "生成队列已满，请稍后重试")
                    .eq(AiGenerationTaskEntity::getId, taskId)
                    .eq(AiGenerationTaskEntity::getTaskStatus, AiGenerationTaskStatus.PENDING.getCode())
                    .eq(AiGenerationTaskEntity::getDeleted, NORMAL));
        }
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.value(), message);
    }
}
