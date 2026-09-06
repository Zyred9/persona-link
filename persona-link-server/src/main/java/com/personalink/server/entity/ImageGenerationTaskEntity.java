package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * AI 双图任务，对应 t_image_generation_task。
 * <p>保存路由和上游任务编号，重试不能随全局配置切换服务商。</p>
 * @author Codex
 * @since 2026-09-06
 */
@TableName("t_image_generation_task")
public class ImageGenerationTaskEntity extends BaseAssessmentEntity {
    /** 请求幂等编号。 */
    private String requestId;
    /** 关联题型 ID。 */
    private Long testId;
    /** 任务创建人。 */
    private Long operatorId;
    /** 服务商：1千问，2火山。 */
    private Integer provider;
    /** 提交时模型快照。 */
    private String modelName;
    /** 提交时 API 地址，不含密钥。 */
    private String endpoint;
    /** 提交时服务区域。 */
    private String region;
    /** 运营关键词。 */
    private String promptText;
    /** 封面最终提示词。 */
    private String coverPrompt;
    /** 详情最终提示词。 */
    private String detailPrompt;
    /** 封面最终宽度像素，历史任务默认800。 */
    private Integer coverWidth = 800;
    /** 封面最终高度像素，历史任务默认800。 */
    private Integer coverHeight = 800;
    /** 详情最终宽度像素，历史任务默认1100。 */
    private Integer detailWidth = 1100;
    /** 详情最终高度像素，历史任务默认500。 */
    private Integer detailHeight = 500;
    public Integer getCoverWidth() { return this.coverWidth; }
    public void setCoverWidth(Integer coverWidth) { this.coverWidth = coverWidth; }
    public Integer getCoverHeight() { return this.coverHeight; }
    public void setCoverHeight(Integer coverHeight) { this.coverHeight = coverHeight; }
    public Integer getDetailWidth() { return this.detailWidth; }
    public void setDetailWidth(Integer detailWidth) { this.detailWidth = detailWidth; }
    public Integer getDetailHeight() { return this.detailHeight; }
    public void setDetailHeight(Integer detailHeight) { this.detailHeight = detailHeight; }
    /** 封面上游任务 ID。 */
    private String coverTaskId;
    /** 详情上游任务 ID。 */
    private String detailTaskId;
    /** 封面请求是否已开始，1且无任务ID表示提交结果未知。 */
    private Integer coverSubmissionStarted;
    /** 详情请求是否已开始，1且无任务ID表示提交结果未知。 */
    private Integer detailSubmissionStarted;
    /** 转存后的封面图片。 */
    private String coverUrl;
    /** 转存后的详情图片。 */
    private String detailImageUrl;
    /** 状态，参见 ImageGenerationTaskStatus。 */
    private Integer taskStatus;
    /** 最近失败原因。 */
    private String errorMessage;
    /** 采用后的草稿版本 ID。 */
    private Long appliedVersionId;
    /** 生成完成时间。 */
    private LocalDateTime completedAt;
    /** 采用时间。 */
    private LocalDateTime appliedAt;
    public String getRequestId() { return this.requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getTestId() { return this.testId; }
    public void setTestId(Long testId) { this.testId = testId; }
    public Long getOperatorId() { return this.operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public Integer getProvider() { return this.provider; }
    public void setProvider(Integer provider) { this.provider = provider; }
    public String getModelName() { return this.modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getEndpoint() { return this.endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getRegion() { return this.region; }
    public void setRegion(String region) { this.region = region; }
    public String getPromptText() { return this.promptText; }
    public void setPromptText(String promptText) { this.promptText = promptText; }
    public String getCoverPrompt() { return this.coverPrompt; }
    public void setCoverPrompt(String coverPrompt) { this.coverPrompt = coverPrompt; }
    public String getDetailPrompt() { return this.detailPrompt; }
    public void setDetailPrompt(String detailPrompt) { this.detailPrompt = detailPrompt; }
    public String getCoverTaskId() { return this.coverTaskId; }
    public void setCoverTaskId(String coverTaskId) { this.coverTaskId = coverTaskId; }
    public String getDetailTaskId() { return this.detailTaskId; }
    public void setDetailTaskId(String detailTaskId) { this.detailTaskId = detailTaskId; }
    public Integer getCoverSubmissionStarted() { return this.coverSubmissionStarted; }
    public void setCoverSubmissionStarted(Integer coverSubmissionStarted) { this.coverSubmissionStarted = coverSubmissionStarted; }
    public Integer getDetailSubmissionStarted() { return this.detailSubmissionStarted; }
    public void setDetailSubmissionStarted(Integer detailSubmissionStarted) { this.detailSubmissionStarted = detailSubmissionStarted; }
    public String getCoverUrl() { return this.coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getDetailImageUrl() { return this.detailImageUrl; }
    public void setDetailImageUrl(String detailImageUrl) { this.detailImageUrl = detailImageUrl; }
    public Integer getTaskStatus() { return this.taskStatus; }
    public void setTaskStatus(Integer taskStatus) { this.taskStatus = taskStatus; }
    public String getErrorMessage() { return this.errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getAppliedVersionId() { return this.appliedVersionId; }
    public void setAppliedVersionId(Long appliedVersionId) { this.appliedVersionId = appliedVersionId; }
    public LocalDateTime getCompletedAt() { return this.completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getAppliedAt() { return this.appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
}
