package com.personalink.server.service.impl;

import lombok.RequiredArgsConstructor;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.personalink.server.ai.ImageGenerationWorker;
import com.personalink.server.ai.ImageProviderClient;
import com.personalink.server.ai.ImageProviderRoute;
import com.personalink.server.config.OssConfiguration;
import com.personalink.server.dto.ImageGenerationStartRequest;
import com.personalink.server.dto.ImageGenerationTaskResponse;
import com.personalink.server.entity.ImageGenerationTaskEntity;
import com.personalink.server.entity.TestEntity;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.ImageGenerationTaskMapper;
import com.personalink.server.service.AiImageTaskService;
import com.personalink.server.service.ContentService;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;

import static com.personalink.server.enums.ImageGenerationTaskStatus.*;

/** 双图任务创建、幂等重试和采用；付费请求在事务提交后的 worker 中执行。 */
@Service
@RequiredArgsConstructor
public class AiImageTaskServiceImpl extends ServiceImpl<ImageGenerationTaskMapper, ImageGenerationTaskEntity>
        implements AiImageTaskService {
    private static final int NORMAL = 0;
    private final ContentService contentService;
    private final ImageProviderClient providerClient;
    private final ImageGenerationWorker worker;
    private final TransactionTemplate transactionTemplate;
    private final OssConfiguration ossConfiguration;

    @Override
    public ImageGenerationTaskResponse create(Long testId, ImageGenerationStartRequest request, Long operatorId) {
        Long id = this.transactionTemplate.execute(transaction -> {
            TestEntity test = this.lockTest(testId);
            ImageGenerationTaskEntity existing = this.baseMapper.selectOne(
                    Wrappers.<ImageGenerationTaskEntity>lambdaQuery()
                            .eq(ImageGenerationTaskEntity::getRequestId, request.requestId())
                            .eq(ImageGenerationTaskEntity::getDeleted, NORMAL), false);
            if (Objects.nonNull(existing)) {
                if (!testId.equals(existing.getTestId()) || !request.promptText().trim().equals(existing.getPromptText())
                        || !request.coverWidth().equals(existing.getCoverWidth())
                        || !request.coverHeight().equals(existing.getCoverHeight())
                        || !request.detailWidth().equals(existing.getDetailWidth())
                        || !request.detailHeight().equals(existing.getDetailHeight())) {
                    throw this.conflict("请求编号已用于其他生图内容，请重新提交");
                }
                return existing.getId();
            }
            this.requireNoActiveTask(testId);
            ImageProviderRoute route = this.providerClient.currentRoute();
            this.providerClient.requireConfigured(route);
            this.ossConfiguration.validate();
            ImageGenerationTaskEntity task = new ImageGenerationTaskEntity();
            task.setRequestId(request.requestId());
            task.setTestId(testId);
            task.setOperatorId(operatorId);
            task.setProvider(route.provider());
            task.setModelName(route.modelName());
            task.setEndpoint(route.baseUrl());
            task.setRegion(route.region());
            task.setPromptText(request.promptText().trim());
            task.setCoverWidth(request.coverWidth());
            task.setCoverHeight(request.coverHeight());
            task.setDetailWidth(request.detailWidth());
            task.setDetailHeight(request.detailHeight());
            String common = "题型主题：「" + test.getTestName() + "」。题型名称仅用于理解主题，不得自动作为文字绘制到画面中。用户要求：\n"
                    + task.getPromptText() + "\n用户指定的风格和配色优先，未指定则自由选择，不套用另一张图的风格。"
                    + "仅绘制纯插画，不做宣传海报或信息图；画面不含标题、汉字、字母、数字、标签、对话框及伪文字，"
                    + "纸张、书本和屏幕上的内容用无文字的色块表达。构图清晰，不拼接多图；服务商强制的 AI 生成标识除外。";
            task.setCoverPrompt(common + "本次只生成封面图，仅执行用户针对封面的要求和通用要求，不执行仅针对详情图的要求。"
                    + this.resolutionPrompt(task.getCoverWidth(), task.getCoverHeight()));
            task.setDetailPrompt(common + "本次只生成详情图，仅执行用户针对详情图的要求和通用要求，不执行仅针对封面的要求。"
                    + this.resolutionPrompt(task.getDetailWidth(), task.getDetailHeight()));
            task.setCoverSubmissionStarted(0);
            task.setDetailSubmissionStarted(0);
            task.setTaskStatus(PENDING.getCode());
            task.setDeleted(NORMAL);
            this.baseMapper.insert(task);
            return task.getId();
        });
        this.dispatch(id);
        return this.get(id);
    }

    @Override
    public ImageGenerationTaskResponse latest(Long testId) {
        this.contentService.getTest(testId);
        ImageGenerationTaskEntity task = this.baseMapper.selectOne(Wrappers.<ImageGenerationTaskEntity>lambdaQuery()
                .eq(ImageGenerationTaskEntity::getTestId, testId)
                .eq(ImageGenerationTaskEntity::getDeleted, NORMAL)
                .orderByDesc(ImageGenerationTaskEntity::getId)
                .last("LIMIT 1"), false);
        return Objects.isNull(task) ? null : ImageGenerationTaskResponse.build(task);
    }

    @Override
    public ImageGenerationTaskResponse get(Long taskId) {
        ImageGenerationTaskEntity task = this.requireTask(taskId);
        this.contentService.getTest(task.getTestId());
        return ImageGenerationTaskResponse.build(task);
    }

    @Override
    public ImageGenerationTaskResponse retry(Long taskId) {
        ImageGenerationTaskEntity snapshot = this.requireTask(taskId);
        this.transactionTemplate.executeWithoutResult(transaction -> {
            this.lockTest(snapshot.getTestId());
            ImageGenerationTaskEntity task = this.requireTask(taskId);
            if (!Integer.valueOf(FAILED.getCode()).equals(task.getTaskStatus())) {
                throw this.conflict("只有失败的生图任务可以重试");
            }
            this.requireNoActiveTask(task.getTestId());
            if (this.uncertain(task.getCoverSubmissionStarted(), task.getCoverTaskId(), task.getCoverUrl())
                    || this.uncertain(task.getDetailSubmissionStarted(), task.getDetailTaskId(), task.getDetailImageUrl())) {
                throw this.conflict("上游提交结果未知，为避免重复扣费禁止重发。请先在供应商控制台确认任务，再发起新任务");
            }
            this.providerClient.requireConfigured(new ImageProviderRoute(task.getProvider(), task.getModelName(),
                    task.getEndpoint(), task.getRegion()));
            this.ossConfiguration.validate();
            this.baseMapper.update(null, Wrappers.<ImageGenerationTaskEntity>lambdaUpdate()
                    .set(ImageGenerationTaskEntity::getTaskStatus, PENDING.getCode())
                    .set(ImageGenerationTaskEntity::getErrorMessage, null)
                    .eq(ImageGenerationTaskEntity::getId, taskId)
                    .eq(ImageGenerationTaskEntity::getTaskStatus, FAILED.getCode())
                    .eq(ImageGenerationTaskEntity::getDeleted, NORMAL));
        });
        this.dispatch(taskId);
        return this.get(taskId);
    }

    @Override
    public ImageGenerationTaskResponse apply(Long taskId, Long operatorId) {
        ImageGenerationTaskEntity snapshot = this.requireTask(taskId);
        this.transactionTemplate.executeWithoutResult(transaction -> {
            this.lockTest(snapshot.getTestId());
            ImageGenerationTaskEntity task = this.requireTask(taskId);
            if (Integer.valueOf(APPLIED.getCode()).equals(task.getTaskStatus())) {
                return;
            }
            if (!Integer.valueOf(SUCCEEDED.getCode()).equals(task.getTaskStatus())
                    || !this.hasText(task.getCoverUrl()) || !this.hasText(task.getDetailImageUrl())) {
                throw this.conflict("两张图片生成成功后才能采用");
            }
            long newer = this.baseMapper.selectCount(Wrappers.<ImageGenerationTaskEntity>lambdaQuery()
                    .eq(ImageGenerationTaskEntity::getTestId, task.getTestId())
                    .eq(ImageGenerationTaskEntity::getTaskStatus, APPLIED.getCode())
                    .gt(ImageGenerationTaskEntity::getId, task.getId())
                    .eq(ImageGenerationTaskEntity::getDeleted, NORMAL));
            if (newer > 0) {
                throw this.conflict("该题型已采用更新的生图结果，不能用旧任务覆盖");
            }
            Long versionId = this.contentService.applyGeneratedImages(task.getTestId(), task.getCoverUrl(),
                    task.getDetailImageUrl(), operatorId);
            this.baseMapper.update(null, Wrappers.<ImageGenerationTaskEntity>lambdaUpdate()
                    .set(ImageGenerationTaskEntity::getTaskStatus, APPLIED.getCode())
                    .set(ImageGenerationTaskEntity::getAppliedVersionId, versionId)
                    .set(ImageGenerationTaskEntity::getAppliedAt, LocalDateTime.now())
                    .eq(ImageGenerationTaskEntity::getId, taskId)
                    .eq(ImageGenerationTaskEntity::getTaskStatus, SUCCEEDED.getCode())
                    .eq(ImageGenerationTaskEntity::getDeleted, NORMAL));
        });
        return this.get(taskId);
    }

    private ImageGenerationTaskEntity requireTask(Long taskId) {
        ImageGenerationTaskEntity task = this.baseMapper.selectById(taskId);
        if (Objects.isNull(task) || !Integer.valueOf(NORMAL).equals(task.getDeleted())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 404, "生图任务不存在或已删除");
        }
        return task;
    }

    private TestEntity lockTest(Long testId) {
        TestEntity test = this.contentService.getOne(Wrappers.<TestEntity>lambdaQuery()
                .eq(TestEntity::getId, testId)
                .eq(TestEntity::getDeleted, NORMAL)
                .last("FOR UPDATE"), false);
        if (Objects.isNull(test)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 404, "题型不存在或已删除");
        }
        return test;
    }

    private void requireNoActiveTask(Long testId) {
        if (this.baseMapper.selectCount(Wrappers.<ImageGenerationTaskEntity>lambdaQuery()
                .eq(ImageGenerationTaskEntity::getTestId, testId)
                .in(ImageGenerationTaskEntity::getTaskStatus, PENDING.getCode(), GENERATING.getCode())
                .eq(ImageGenerationTaskEntity::getDeleted, NORMAL)) > 0) {
            throw this.conflict("该题型已有图片生成中，请等待完成");
        }
    }

    private void dispatch(Long id) {
        try {
            this.worker.generateAsync(id);
        } catch (TaskRejectedException exception) {
            this.baseMapper.update(null, Wrappers.<ImageGenerationTaskEntity>lambdaUpdate()
                    .set(ImageGenerationTaskEntity::getTaskStatus, FAILED.getCode())
                    .set(ImageGenerationTaskEntity::getErrorMessage, "生成队列已满，请稍后重试")
                    .eq(ImageGenerationTaskEntity::getId, id)
                    .eq(ImageGenerationTaskEntity::getTaskStatus, PENDING.getCode())
                    .eq(ImageGenerationTaskEntity::getDeleted, NORMAL));
        }
    }

    private String resolutionPrompt(int width, int height) {
        return "宽高比" + width + ":" + height + "，最终尺寸" + width + "×" + height
                + "像素；以上尺寸优先于用户描述中的尺寸，按此比例独立完整构图，不拉伸，请预留边缘安全区。";
    }

    private boolean uncertain(Integer started, String upstreamId, String imageUrl) {
        return Integer.valueOf(1).equals(started) && !this.hasText(upstreamId) && !this.hasText(imageUrl);
    }

    private boolean hasText(String value) { return Objects.nonNull(value) && !value.isBlank(); }
    private BusinessException conflict(String message) { return new BusinessException(HttpStatus.CONFLICT, 409, message); }
}
