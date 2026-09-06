package com.personalink.server.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalink.server.config.ImageGenerationProperties;
import com.personalink.server.entity.ImageGenerationTaskEntity;
import com.personalink.server.mapper.ImageGenerationTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.personalink.server.enums.ImageGenerationTaskStatus.*;

/** 上游原生异步任务执行器；先持久化提交标记，再提交，得到任务 ID 后只查询。 */
@Component
public class ImageGenerationWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageGenerationWorker.class);
    private final ImageGenerationTaskMapper mapper;
    private final ImageProviderClient client;
    private final GeneratedImageStorage storage;
    private final ImageGenerationProperties properties;

    public ImageGenerationWorker(ImageGenerationTaskMapper mapper, ImageProviderClient client,
            GeneratedImageStorage storage, ImageGenerationProperties properties) {
        this.mapper = mapper;
        this.client = client;
        this.storage = storage;
        this.properties = properties;
    }

    /** 后台生成两张图；调用方通过任务详情接口查询，关闭页面不影响生成。 */
    @Async("aiImageGenerationExecutor")
    public void generateAsync(Long taskId) {
        if (this.mapper.update(null, Wrappers.<ImageGenerationTaskEntity>lambdaUpdate()
                .set(ImageGenerationTaskEntity::getTaskStatus, GENERATING.getCode())
                .eq(ImageGenerationTaskEntity::getId, taskId)
                .eq(ImageGenerationTaskEntity::getTaskStatus, PENDING.getCode())
                .eq(ImageGenerationTaskEntity::getDeleted, 0)) != 1) return;
        ImageGenerationTaskEntity task = this.mapper.selectById(taskId);
        if (Objects.isNull(task)) return;
        task.setTaskStatus(GENERATING.getCode());
        ImageProviderRoute route = new ImageProviderRoute(task.getProvider(), task.getModelName(),
                task.getEndpoint(), task.getRegion());
        try {
            this.client.requireConfigured(route);
            this.submitMissing(task, route, true);
            this.submitMissing(task, route, false);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(1, this.properties.getTaskTimeoutSeconds()));
            // 每张图片完成后立即落库，避免另一张失败导致已成功图片重复付费。
            while (!this.hasText(task.getCoverUrl()) || !this.hasText(task.getDetailImageUrl())) {
                this.collect(task, route, true);
                this.collect(task, route, false);
                if (this.hasText(task.getCoverUrl()) && this.hasText(task.getDetailImageUrl())) break;
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("等待生图超时，请重试继续查询原任务，不会重复生成已提交的图片");
                }
                Thread.sleep(Math.max(1000, this.properties.getPollIntervalMillis()));
            }
            this.mapper.update(null, Wrappers.<ImageGenerationTaskEntity>lambdaUpdate()
                    .set(ImageGenerationTaskEntity::getTaskStatus, SUCCEEDED.getCode())
                    .set(ImageGenerationTaskEntity::getCompletedAt, LocalDateTime.now())
                    .set(ImageGenerationTaskEntity::getErrorMessage, null)
                    .eq(ImageGenerationTaskEntity::getId, taskId)
                    .eq(ImageGenerationTaskEntity::getTaskStatus, GENERATING.getCode())
                    .eq(ImageGenerationTaskEntity::getDeleted, 0));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.fail(taskId, "服务中断，请重试继续查询已提交的任务");
        } catch (RuntimeException exception) {
            // 远程响应和临时 URL 不写入日志或公开错误；记录已脱敏的任务上下文。
            LOGGER.warn("[AI生图] 任务执行失败，任务ID：{}，服务商：{}，异常类型：{}", taskId,
                    route.provider(), exception.getClass().getSimpleName());
            String message = exception instanceof ImageSubmissionUncertainException
                    ? "上游提交结果未知，请先在供应商控制台确认，避免重复扣费"
                    : exception instanceof IllegalStateException ? exception.getMessage()
                    : "生图服务调用失败，请检查后端配置后重试";
            this.fail(taskId, message);
        }
    }

    /** 服务重启保留上游编号和已完成图片，允许运营明确重试继续查询。 */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        // ponytail: 单实例恢复；多实例部署时改为带过期时间及实例 ID 的任务租约。
        this.mapper.update(null, Wrappers.<ImageGenerationTaskEntity>lambdaUpdate()
                .set(ImageGenerationTaskEntity::getTaskStatus, FAILED.getCode())
                .set(ImageGenerationTaskEntity::getErrorMessage, "服务重启中断等待，请重试继续查询原任务")
                .in(ImageGenerationTaskEntity::getTaskStatus, PENDING.getCode(), GENERATING.getCode())
                .eq(ImageGenerationTaskEntity::getDeleted, 0));
    }

    private void submitMissing(ImageGenerationTaskEntity task, ImageProviderRoute route, boolean cover) {
        String imageUrl = cover ? task.getCoverUrl() : task.getDetailImageUrl();
        String upstreamId = cover ? task.getCoverTaskId() : task.getDetailTaskId();
        if (this.hasText(imageUrl) || this.hasText(upstreamId)) return;
        Integer started = cover ? task.getCoverSubmissionStarted() : task.getDetailSubmissionStarted();
        if (Integer.valueOf(1).equals(started)) {
            throw new IllegalStateException("上游提交结果未知，请先在供应商控制台确认，避免重复扣费");
        }
        int marked = this.mapper.update(null, Wrappers.<ImageGenerationTaskEntity>lambdaUpdate()
                .set(cover ? ImageGenerationTaskEntity::getCoverSubmissionStarted
                        : ImageGenerationTaskEntity::getDetailSubmissionStarted, 1)
                .eq(ImageGenerationTaskEntity::getId, task.getId())
                .eq(ImageGenerationTaskEntity::getTaskStatus, GENERATING.getCode())
                .eq(ImageGenerationTaskEntity::getDeleted, 0));
        if (marked != 1) throw new IllegalStateException("生图任务状态已变化，已停止提交");
        String id;
        try {
            id = this.client.submit(route, cover ? task.getCoverPrompt() : task.getDetailPrompt(),
                    cover ? task.getCoverWidth() : task.getDetailWidth(),
                    cover ? task.getCoverHeight() : task.getDetailHeight());
        } catch (ImageSubmissionUncertainException exception) {
            throw exception;
        } catch (ImageSubmissionRejectedException exception) {
            this.mapper.update(null, Wrappers.<ImageGenerationTaskEntity>lambdaUpdate()
                    .set(cover ? ImageGenerationTaskEntity::getCoverSubmissionStarted
                            : ImageGenerationTaskEntity::getDetailSubmissionStarted, 0)
                    .eq(ImageGenerationTaskEntity::getId, task.getId())
                    .eq(ImageGenerationTaskEntity::getDeleted, 0));
            throw exception;
        }
        this.mapper.update(null, Wrappers.<ImageGenerationTaskEntity>lambdaUpdate()
                .set(cover ? ImageGenerationTaskEntity::getCoverTaskId : ImageGenerationTaskEntity::getDetailTaskId, id)
                .eq(ImageGenerationTaskEntity::getId, task.getId())
                .eq(ImageGenerationTaskEntity::getDeleted, 0));
        if (cover) task.setCoverTaskId(id); else task.setDetailTaskId(id);
    }

    private void collect(ImageGenerationTaskEntity task, ImageProviderRoute route, boolean cover) {
        if (this.hasText(cover ? task.getCoverUrl() : task.getDetailImageUrl())) return;
        ImageProviderResult result;
        try {
            result = this.client.query(route, cover ? task.getCoverTaskId() : task.getDetailTaskId());
        } catch (RuntimeException exception) {
            // 查询可安全重试；绝不因查询超时重新提交付费请求。
            return;
        }
        if (result.state() == 1) return;
        if (result.state() == 3) {
            this.mapper.update(null, Wrappers.<ImageGenerationTaskEntity>lambdaUpdate()
                    .set(cover ? ImageGenerationTaskEntity::getCoverTaskId : ImageGenerationTaskEntity::getDetailTaskId, null)
                    .set(cover ? ImageGenerationTaskEntity::getCoverSubmissionStarted
                            : ImageGenerationTaskEntity::getDetailSubmissionStarted, 0)
                    .eq(ImageGenerationTaskEntity::getId, task.getId())
                    .eq(ImageGenerationTaskEntity::getDeleted, 0));
            throw new IllegalStateException((cover ? "封面图" : "详情图") + "生成失败，请调整关键词或重试");
        }
        String stored = this.storage.store(result.imageUrl(), cover ? task.getCoverWidth() : task.getDetailWidth(),
                cover ? task.getCoverHeight() : task.getDetailHeight());
        this.mapper.update(null, Wrappers.<ImageGenerationTaskEntity>lambdaUpdate()
                .set(cover ? ImageGenerationTaskEntity::getCoverUrl : ImageGenerationTaskEntity::getDetailImageUrl, stored)
                .eq(ImageGenerationTaskEntity::getId, task.getId())
                .eq(ImageGenerationTaskEntity::getDeleted, 0));
        if (cover) task.setCoverUrl(stored); else task.setDetailImageUrl(stored);
    }

    private void fail(Long id, String message) {
        this.mapper.update(null, Wrappers.<ImageGenerationTaskEntity>lambdaUpdate()
                .set(ImageGenerationTaskEntity::getTaskStatus, FAILED.getCode())
                .set(ImageGenerationTaskEntity::getErrorMessage, message)
                .eq(ImageGenerationTaskEntity::getId, id)
                .eq(ImageGenerationTaskEntity::getTaskStatus, GENERATING.getCode())
                .eq(ImageGenerationTaskEntity::getDeleted, 0));
    }

    private boolean hasText(String value) { return Objects.nonNull(value) && !value.isBlank(); }
}
