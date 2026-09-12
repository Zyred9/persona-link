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
import com.personalink.server.mapper.AiGenerationTaskMapper;
import com.personalink.server.service.ContentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
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
        Map<Integer, String> failures = new HashMap<>();
        while (task.getCompletedBatchCount() < task.getTotalBatchCount()) {
            int batchNo = task.getCompletedBatchCount() + 1;
            int firstQuestionNo = (batchNo - 1) * BATCH_SIZE + 1;
            int questionCount = Math.min(BATCH_SIZE,
                    task.getTargetQuestionCount() - firstQuestionNo + 1);
            this.generateRange(task, test, version.dimensions(), dimensions, dimensionCodes,
                    firstQuestionNo, questionCount, true, failures);
            task = this.requireTask(taskId);
        }
        // 首轮先走完所有题号；每轮遍历全部缺号，避免某一道持续失败阻塞后面的补题。
        while (task.getGeneratedQuestionCount() < task.getTargetQuestionCount()) {
            Set<Integer> savedNos = this.contentService.listQuestions(task.getVersionId()).stream()
                    .map(QuestionResponse::questionNo).collect(Collectors.toSet());
            for (int first = 1; first <= task.getTargetQuestionCount(); first++) {
                if (savedNos.contains(first)) {
                    continue;
                }
                int count = 1;
                while (count < BATCH_SIZE && first + count <= task.getTargetQuestionCount()
                        && !savedNos.contains(first + count)) {
                    count++;
                }
                this.generateRange(task, test, version.dimensions(), dimensions, dimensionCodes,
                        first, count, false, failures);
                task = this.requireTask(taskId);
                first += count - 1;
            }
        }
        this.markPendingReview(taskId);
    }

    private void generateRange(AiGenerationTaskEntity task, TestResponse test,
                               List<ScoreDimensionResponse> savedDimensions,
                               List<AiGeneratedDimension> dimensions, Set<String> dimensionCodes,
                               int firstQuestionNo, int questionCount, boolean firstPass,
                               Map<Integer, String> failures) {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("AI 生成任务已中断，可重试继续补题");
        }
        int batchNo = (firstQuestionNo - 1) / BATCH_SIZE + 1;
        this.markCurrentBatch(task.getId(), batchNo);
        List<String> existingTexts = this.contentService.listQuestions(task.getVersionId()).stream()
                .map(QuestionResponse::questionText).toList();
        if (!firstPass) {
            for (int no = firstQuestionNo; no < firstQuestionNo + questionCount; no++) {
                failures.putIfAbsent(no, "该题此前未成功保存，请换用不同场景并严格遵循题目格式和计分规则");
            }
        }
        this.generateValidQuestionBatch(task.getId(), task.getModelName(), test.getTestName(), test.getTestType(),
                task.getPromptText(), dimensions, dimensionCodes, firstQuestionNo, questionCount, existingTexts, failures,
                batch -> this.persistQuestionBatch(task.getId(), batchNo,
                        this.toQuestionRequests(batch, savedDimensions), firstPass));
    }

    AiGeneratedSetup generateValidSetup(Long taskId,
                                        String modelName,
                                        String testName,
                                        Integer testType,
                                        String promptText) {
        return this.generateWithValidation(taskId, "维度和结果规则",
                feedback -> this.deepSeekClient.generateSetup(
                        modelName, testName, testType, promptText, feedback),
                AiGenerationValidator::validateSetup, "");
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
                                                         Map<Integer, String> failures,
                                                         Consumer<AiGeneratedQuestionBatch> persistBatch) {
        String previousFailures = failures.entrySet().stream()
                .filter(entry -> entry.getKey() >= firstQuestionNo && entry.getKey() < firstQuestionNo + questionCount)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "第" + entry.getKey() + "题：" + entry.getValue()).collect(Collectors.joining("；"));
        AiGeneratedQuestionBatch generated;
        try {
            generated = this.generateWithValidation(taskId, "题目批次 " + firstQuestionNo,
                    feedback -> this.deepSeekClient.generateQuestionBatch(modelName, testName, testType, promptText,
                            dimensions, firstQuestionNo, questionCount, existingQuestionTexts, feedback),
                    Function.identity(), previousFailures);
        } catch (BusinessException exception) {
            if (exception.getCode() != INVALID_AI_RESPONSE_CODE) {
                throw exception;
            }
            LOGGER.warn("[AI题库] 本批返回格式无效，任务ID：{}，起始题号：{}，已延后补题", taskId, firstQuestionNo);
            generated = new AiGeneratedQuestionBatch(List.of());
        }
        List<AiGeneratedQuestion> accepted = new ArrayList<>();
        List<String> excludedTexts = new ArrayList<>(existingQuestionTexts);
        List<AiGeneratedQuestion> candidates = Objects.isNull(generated) || Objects.isNull(generated.questions())
                ? List.of() : generated.questions();
        for (int questionNo = firstQuestionNo; questionNo < firstQuestionNo + questionCount; questionNo++) {
            int expectedNo = questionNo;
            List<AiGeneratedQuestion> matches = candidates.stream().filter(Objects::nonNull)
                    .filter(question -> Integer.valueOf(expectedNo).equals(question.questionNo())).toList();
            try {
                AiGenerationValidator.validateQuestionBatch(new AiGeneratedQuestionBatch(matches),
                        dimensionCodes, expectedNo, 1, excludedTexts);
                accepted.add(matches.get(0));
                excludedTexts.add(matches.get(0).questionText());
                failures.remove(questionNo);
            } catch (IllegalArgumentException exception) {
                failures.put(questionNo, exception.getMessage());
                LOGGER.warn("[AI题库] 题目校验失败，任务ID：{}，题号：{}，已延后补题：{}",
                        taskId, questionNo, exception.getMessage());
            }
        }
        AiGeneratedQuestionBatch result = new AiGeneratedQuestionBatch(accepted);
        persistBatch.accept(result);
        return result;
    }

    private <T> T generateWithValidation(Long taskId,
                                         String contentName,
                                         Function<String, T> generator,
                                         Function<T, T> validator,
                                         String initialFeedback) {
        String validationFeedback = initialFeedback;
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("AI 生成任务已中断，可重试继续补题");
            }
            try {
                return validator.apply(generator.apply(validationFeedback));
            } catch (RuntimeException exception) {
                if (!this.isRetryableGenerationError(exception) || attempt == MAX_GENERATION_ATTEMPTS) {
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
                    .eq(AiGenerationTaskEntity::getDeleted, NORMAL));
            if (updated != 1) {
                throw new IllegalStateException("AI 生成任务批次状态已变化");
            }
        });
    }

    private void persistQuestionBatch(Long taskId,
                                      int batchNo,
                                      List<QuestionSaveRequest> questions,
                                      boolean firstPass) {
        this.transactionTemplate.executeWithoutResult(status -> {
            AiGenerationTaskEntity task = this.requireGeneratingTaskForUpdate(taskId);
            if (firstPass && task.getCompletedBatchCount() >= batchNo) {
                return;
            }
            if (firstPass && task.getCompletedBatchCount() != batchNo - 1) {
                throw new IllegalStateException("AI 生成任务批次提交顺序错误");
            }
            int savedCount = questions.isEmpty() ? 0 : this.contentService.appendGeneratedQuestions(
                    task.getVersionId(), questions, task.getOperatorId());
            AiGenerationTaskEntity update = new AiGenerationTaskEntity();
            update.setId(taskId);
            update.setCurrentBatchNo(batchNo);
            update.setCompletedBatchCount(firstPass ? batchNo : task.getCompletedBatchCount());
            update.setGeneratedQuestionCount(task.getGeneratedQuestionCount() + savedCount);
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
