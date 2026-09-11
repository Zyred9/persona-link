package com.personalink.server.ai;

import lombok.RequiredArgsConstructor;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalink.server.dto.AiGeneratedDimension;
import com.personalink.server.dto.AiGeneratedOption;
import com.personalink.server.dto.AiGeneratedQuestion;
import com.personalink.server.dto.AiGeneratedQuestionBatch;
import com.personalink.server.dto.AiGeneratedResultRule;
import com.personalink.server.dto.AiGeneratedSetup;
import com.personalink.server.dto.QuestionOptionSaveRequest;
import com.personalink.server.dto.QuestionResponse;
import com.personalink.server.dto.QuestionSaveRequest;
import com.personalink.server.dto.ResultConfigSaveRequest;
import com.personalink.server.dto.ResultTemplateSaveRequest;
import com.personalink.server.dto.ScoreDimensionResponse;
import com.personalink.server.dto.ScoreDimensionSaveRequest;
import com.personalink.server.dto.TestResponse;
import com.personalink.server.dto.TestVersionResponse;
import com.personalink.server.dto.TestVersionSaveRequest;
import com.personalink.server.entity.AiGenerationTaskEntity;
import com.personalink.server.enums.AiGenerationTaskStatus;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.exception.AiQuestionTextConflictException;
import com.personalink.server.mapper.AiGenerationTaskMapper;
import com.personalink.server.service.ContentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** 串行生成单个 AI 题库任务的异步 Worker。 */
@Component
@RequiredArgsConstructor
public class AiQuestionGenerationWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiQuestionGenerationWorker.class);
    private static final int NORMAL = 0;
    private static final int GENERATED = 1;
    private static final int BATCH_SIZE = 10;
    private static final int MAX_GENERATION_ATTEMPTS = 3;
    private static final int RETRYABLE_AI_SERVICE_CODE = 50231;
    private static final int INVALID_AI_RESPONSE_CODE = 50232;

    private final AiGenerationTaskMapper taskMapper;
    private final ContentService contentService;
    private final DeepSeekClient deepSeekClient;
    private final TransactionTemplate transactionTemplate;

    /**
     * 异步执行生成任务；同一任务通过数据库状态原子抢占。
     *
     * @param taskId AI 生成任务 ID
     */
    @Async("aiQuestionGenerationExecutor")
    public void generateAsync(Long taskId) {
        if (!this.claim(taskId)) {
            return;
        }
        try {
            this.generate(taskId);
        } catch (Exception exception) {
            LOGGER.error("[AI题库] 生成任务失败，任务ID：{}", taskId, exception);
            this.markFailed(taskId, exception.getMessage());
        }
    }

    private void generate(Long taskId) {
        AiGenerationTaskEntity task = this.requireTask(taskId);
        TestVersionResponse version = this.contentService.getVersion(task.getVersionId());
        TestResponse test = this.contentService.getTest(version.testId());
        if (!Integer.valueOf(GENERATED).equals(task.getDimensionGeneratedFlag())
                || !Integer.valueOf(GENERATED).equals(task.getResultRuleGeneratedFlag())) {
            if (!Objects.equals(task.getDimensionGeneratedFlag(), task.getResultRuleGeneratedFlag())) {
                throw new IllegalStateException("AI 维度和结果规则进度不一致");
            }
            AiGeneratedSetup setup = this.generateValidSetup(taskId, task.getModelName(), test.getTestName(),
                    test.getTestType(), task.getPromptText());
            this.persistSetup(taskId, setup);
            task = this.requireTask(taskId);
            version = this.contentService.getVersion(task.getVersionId());
        }
        List<AiGeneratedDimension> dimensions = version.dimensions().stream()
                .map(item -> new AiGeneratedDimension(item.dimensionCode(), item.dimensionName(), item.sortNo()))
                .toList();
        Set<String> dimensionCodes = dimensions.stream()
                .map(AiGeneratedDimension::dimensionCode).collect(Collectors.toSet());
        if (dimensionCodes.isEmpty()) {
            throw new IllegalStateException("AI 计分维度未成功落库");
        }
        while (task.getGeneratedQuestionCount() < task.getTargetQuestionCount()) {
            int batchNo = task.getCompletedBatchCount() + 1;
            int firstQuestionNo = task.getGeneratedQuestionCount() + 1;
            int questionCount = Math.min(BATCH_SIZE,
                    task.getTargetQuestionCount() - task.getGeneratedQuestionCount());
            this.markCurrentBatch(taskId, batchNo);
            List<String> existingQuestionTexts = this.contentService.listQuestions(task.getVersionId()).stream()
                    .map(QuestionResponse::questionText).toList();
            List<ScoreDimensionResponse> savedDimensions = version.dimensions();
            this.generateValidQuestionBatch(taskId, task.getModelName(),
                    test.getTestName(), test.getTestType(), task.getPromptText(), dimensions, dimensionCodes,
                    firstQuestionNo, questionCount, existingQuestionTexts,
                    batch -> this.persistQuestionBatch(taskId, batchNo,
                            this.toQuestionRequests(batch, savedDimensions)));
            task = this.requireTask(taskId);
        }
        this.markPendingReview(taskId);
    }

    AiGeneratedSetup generateValidSetup(Long taskId,
                                        String modelName,
                                        String testName,
                                        Integer testType,
                                        String promptText) {
        return this.generateWithValidation(taskId, "维度和结果规则",
                feedback -> this.deepSeekClient.generateSetup(
                        modelName, testName, testType, promptText, feedback),
                AiGenerationValidator::validateSetup, setup -> { });
    }

    AiGeneratedQuestionBatch generateValidQuestionBatch(Long taskId,
                                                         String modelName,
                                                         String testName,
                                                         Integer testType,
                                                         String promptText,
                                                         List<AiGeneratedDimension> dimensions,
                                                         Set<String> dimensionCodes,
                                                         int firstQuestionNo,
                                                         int questionCount,
                                                         List<String> existingQuestionTexts,
                                                         Consumer<AiGeneratedQuestionBatch> persistBatch) {
        return this.generateWithValidation(taskId, "题目批次 " + firstQuestionNo + "-"
                        + (firstQuestionNo + questionCount - 1),
                feedback -> this.deepSeekClient.generateQuestionBatch(modelName, testName, testType, promptText,
                        dimensions, firstQuestionNo, questionCount, existingQuestionTexts, feedback),
                batch -> {
                    AiGenerationValidator.validateQuestionBatch(
                            batch, dimensionCodes, firstQuestionNo, questionCount, existingQuestionTexts);
                    return batch;
                }, persistBatch);
    }

    private <T> T generateWithValidation(Long taskId,
                                         String contentName,
                                         Function<String, T> generator,
                                         Function<T, T> validator,
                                         Consumer<T> persister) {
        String validationFeedback = "";
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            boolean persisting = false;
            try {
                T result = validator.apply(generator.apply(validationFeedback));
                persisting = true;
                // SQL 判重更宽时，必须等待整批事务回滚后再生成；其它持久化故障直接失败。
                persister.accept(result);
                return result;
            } catch (RuntimeException exception) {
                if ((persisting && !(exception instanceof AiQuestionTextConflictException))
                        || !this.isRetryableGenerationError(exception) || attempt == MAX_GENERATION_ATTEMPTS) {
                    throw exception;
                }
                validationFeedback = exception.getMessage();
                LOGGER.warn("[AI题库] {}校验失败，任务ID：{}，第{}次生成，将自动重试：{}",
                        contentName, taskId, attempt, validationFeedback);
            }
        }
        throw new IllegalStateException("AI 内容生成状态异常");
    }

    private boolean isRetryableGenerationError(RuntimeException exception) {
        return exception instanceof IllegalArgumentException
                || exception instanceof AiQuestionTextConflictException
                || (exception instanceof BusinessException businessException
                && (businessException.getCode() == RETRYABLE_AI_SERVICE_CODE
                || businessException.getCode() == INVALID_AI_RESPONSE_CODE));
    }

    private boolean claim(Long taskId) {
        Integer updated = this.transactionTemplate.execute(status -> this.taskMapper.update(null,
                Wrappers.<AiGenerationTaskEntity>lambdaUpdate()
                        .set(AiGenerationTaskEntity::getTaskStatus, AiGenerationTaskStatus.GENERATING.getCode())
                        .set(AiGenerationTaskEntity::getErrorMessage, null)
                        .eq(AiGenerationTaskEntity::getId, taskId)
                        .eq(AiGenerationTaskEntity::getTaskStatus, AiGenerationTaskStatus.PENDING.getCode())
                        .eq(AiGenerationTaskEntity::getDeleted, NORMAL)));
        return Objects.nonNull(updated) && updated == 1;
    }

    private void persistSetup(Long taskId, AiGeneratedSetup setup) {
        this.transactionTemplate.executeWithoutResult(status -> {
            AiGenerationTaskEntity task = this.requireGeneratingTaskForUpdate(taskId);
            if (Integer.valueOf(GENERATED).equals(task.getDimensionGeneratedFlag())
                    && Integer.valueOf(GENERATED).equals(task.getResultRuleGeneratedFlag())) {
                return;
            }
            TestVersionResponse version = this.contentService.getVersion(task.getVersionId());
            List<ScoreDimensionSaveRequest> dimensions = setup.dimensions().stream()
                    .map(item -> new ScoreDimensionSaveRequest(null, item.dimensionCode().trim(),
                            item.dimensionName().trim(), Objects.isNull(item.sortNo()) ? 0 : item.sortNo()))
                    .toList();
            TestVersionResponse updatedVersion = this.contentService.updateVersion(version.id(),
                    new TestVersionSaveRequest(version.title(), version.coverUrl(), version.detailImageUrl(), version.description(),
                            version.estimatedMinutes(), version.drawQuestionCount(), version.versionNote(), dimensions),
                    task.getOperatorId());
            Map<String, Long> dimensionIdMap = updatedVersion.dimensions().stream().collect(
                    Collectors.toMap(ScoreDimensionResponse::dimensionCode, ScoreDimensionResponse::id));
            List<ResultTemplateSaveRequest> resultRules = setup.resultRules().stream()
                    .map(item -> this.toResultRequest(item, dimensionIdMap)).toList();
            this.contentService.saveResultConfig(version.id(), new ResultConfigSaveRequest(resultRules),
                    task.getOperatorId());
            AiGenerationTaskEntity update = new AiGenerationTaskEntity();
            update.setId(taskId);
            update.setDimensionGeneratedFlag(GENERATED);
            update.setResultRuleGeneratedFlag(GENERATED);
            this.taskMapper.updateById(update);
        });
    }

    private void markCurrentBatch(Long taskId, int batchNo) {
        this.transactionTemplate.executeWithoutResult(status -> {
            int updated = this.taskMapper.update(null, Wrappers.<AiGenerationTaskEntity>lambdaUpdate()
                    .set(AiGenerationTaskEntity::getCurrentBatchNo, batchNo)
                    .eq(AiGenerationTaskEntity::getId, taskId)
                    .eq(AiGenerationTaskEntity::getTaskStatus, AiGenerationTaskStatus.GENERATING.getCode())
                    .eq(AiGenerationTaskEntity::getCompletedBatchCount, batchNo - 1)
                    .eq(AiGenerationTaskEntity::getDeleted, NORMAL));
            if (updated != 1) {
                throw new IllegalStateException("AI 生成任务批次状态已变化");
            }
        });
    }

    private void persistQuestionBatch(Long taskId,
                                      int batchNo,
                                      List<QuestionSaveRequest> questions) {
        this.transactionTemplate.executeWithoutResult(status -> {
            AiGenerationTaskEntity task = this.requireGeneratingTaskForUpdate(taskId);
            if (task.getCompletedBatchCount() >= batchNo) {
                return;
            }
            if (task.getCompletedBatchCount() != batchNo - 1) {
                throw new IllegalStateException("AI 生成任务批次提交顺序错误");
            }
            this.contentService.appendGeneratedQuestions(
                    task.getVersionId(), questions, task.getOperatorId());
            AiGenerationTaskEntity update = new AiGenerationTaskEntity();
            update.setId(taskId);
            update.setCurrentBatchNo(batchNo);
            update.setCompletedBatchCount(batchNo);
            update.setGeneratedQuestionCount(task.getGeneratedQuestionCount() + questions.size());
            this.taskMapper.updateById(update);
        });
    }

    private void markPendingReview(Long taskId) {
        this.transactionTemplate.executeWithoutResult(status -> {
            AiGenerationTaskEntity task = this.requireGeneratingTaskForUpdate(taskId);
            if (!Integer.valueOf(GENERATED).equals(task.getDimensionGeneratedFlag())
                    || !Integer.valueOf(GENERATED).equals(task.getResultRuleGeneratedFlag())
                    || !Objects.equals(task.getGeneratedQuestionCount(), task.getTargetQuestionCount())
                    || !Objects.equals(task.getCompletedBatchCount(), task.getTotalBatchCount())) {
                throw new IllegalStateException("AI 生成任务尚未完整生成");
            }
            AiGenerationTaskEntity update = new AiGenerationTaskEntity();
            update.setId(taskId);
            update.setTaskStatus(AiGenerationTaskStatus.PENDING_REVIEW.getCode());
            update.setCompletedAt(LocalDateTime.now());
            this.taskMapper.updateById(update);
        });
    }

    private void markFailed(Long taskId, String errorMessage) {
        String message = Objects.isNull(errorMessage) || errorMessage.isBlank()
                ? "生成失败，请稍后重试" : errorMessage;
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        String finalMessage = message;
        this.transactionTemplate.executeWithoutResult(status -> this.taskMapper.update(null,
                Wrappers.<AiGenerationTaskEntity>lambdaUpdate()
                        .set(AiGenerationTaskEntity::getTaskStatus, AiGenerationTaskStatus.FAILED.getCode())
                        .set(AiGenerationTaskEntity::getErrorMessage, finalMessage)
                        .eq(AiGenerationTaskEntity::getId, taskId)
                        .eq(AiGenerationTaskEntity::getTaskStatus, AiGenerationTaskStatus.GENERATING.getCode())
                        .eq(AiGenerationTaskEntity::getDeleted, NORMAL)));
    }

    private List<QuestionSaveRequest> toQuestionRequests(AiGeneratedQuestionBatch batch,
                                                         List<ScoreDimensionResponse> dimensions) {
        Map<String, Long> dimensionIdMap = dimensions.stream().collect(
                Collectors.toMap(ScoreDimensionResponse::dimensionCode, ScoreDimensionResponse::id));
        return batch.questions().stream()
                .sorted(Comparator.comparing(AiGeneratedQuestion::questionNo))
                .map(question -> new QuestionSaveRequest(question.questionType(),
                        dimensionIdMap.get(question.dimensionCode()), question.minSelectCount(),
                        question.maxSelectCount(), question.questionNo(), question.questionText().trim(),
                        question.requiredFlag(), Objects.isNull(question.sortNo())
                        ? question.questionNo() : question.sortNo(), question.options().stream()
                        .map(option -> this.toOptionRequest(option, question.questionType(), dimensionIdMap))
                        .toList()))
                .toList();
    }

    private QuestionOptionSaveRequest toOptionRequest(AiGeneratedOption option,
                                                      Integer questionType,
                                                      Map<String, Long> dimensionIdMap) {
        Long dimensionId = Integer.valueOf(2).equals(questionType)
                ? dimensionIdMap.get(option.dimensionCode()) : null;
        return new QuestionOptionSaveRequest(option.optionCode().trim(), option.optionText().trim(),
                dimensionId, option.scoreValue(), Objects.isNull(option.sortNo()) ? 0 : option.sortNo());
    }

    private ResultTemplateSaveRequest toResultRequest(AiGeneratedResultRule rule,
                                                       Map<String, Long> dimensionIdMap) {
        return new ResultTemplateSaveRequest(null, dimensionIdMap.get(rule.dimensionCode()),
                rule.resultCode().trim(), rule.resultName().trim(), rule.scoreMin(), rule.scoreMax(),
                rule.basicResultJson(), rule.deepResultJson(), rule.shareCopyJson(),
                Objects.isNull(rule.sortNo()) ? 0 : rule.sortNo());
    }

    private AiGenerationTaskEntity requireTask(Long taskId) {
        AiGenerationTaskEntity task = this.taskMapper.selectById(taskId);
        if (Objects.isNull(task)) {
            throw new IllegalStateException("AI 生成任务不存在");
        }
        return task;
    }

    private AiGenerationTaskEntity requireGeneratingTaskForUpdate(Long taskId) {
        AiGenerationTaskEntity task = this.taskMapper.selectOne(
                Wrappers.<AiGenerationTaskEntity>lambdaQuery()
                        .eq(AiGenerationTaskEntity::getId, taskId)
                        .eq(AiGenerationTaskEntity::getDeleted, NORMAL)
                        .last("FOR UPDATE"), false);
        if (Objects.isNull(task)
                || !Integer.valueOf(AiGenerationTaskStatus.GENERATING.getCode()).equals(task.getTaskStatus())) {
            throw new IllegalStateException("AI 生成任务状态已变化");
        }
        return task;
    }
}
