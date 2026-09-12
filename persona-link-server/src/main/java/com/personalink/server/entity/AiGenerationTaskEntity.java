package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** AI 题库生成任务实体，对应 t_ai_generation_task。 */
@TableName("t_ai_generation_task")
public class AiGenerationTaskEntity extends BaseAssessmentEntity {

    /** 任务业务编号。 */
    private String taskNo;
    /** 防重复请求编号。 */
    private String requestId;
    /** 草稿版本 ID。 */
    private Long versionId;
    /** 创建任务的后台操作人 ID。 */
    private Long operatorId;
    /** 实际调用的模型名称。 */
    private String modelName;
    /** 运营生成要求。 */
    private String promptText;
    /** 目标题目数。 */
    private Integer targetQuestionCount;
    /** 当前批次号。 */
    private Integer currentBatchNo;
    /** 总批次数。 */
    private Integer totalBatchCount;
    /** 首轮已处理批次数，失败题在首轮结束后补齐。 */
    private Integer completedBatchCount;
    /** 已生成题目数。 */
    private Integer generatedQuestionCount;
    /** 已重试次数。 */
    private Integer retryCount;
    /** 维度是否已生成。 */
    private Integer dimensionGeneratedFlag;
    /** 结果规则是否已生成。 */
    private Integer resultRuleGeneratedFlag;
    /** 任务状态。 */
    private Integer taskStatus;
    /** 最近失败原因。 */
    private String errorMessage;
    /** 生成完成时间。 */
    private LocalDateTime completedAt;
    /** 提交题型库时间。 */
    private LocalDateTime submittedAt;

    public String getTaskNo() { return this.taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getRequestId() { return this.requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getVersionId() { return this.versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Long getOperatorId() { return this.operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getModelName() { return this.modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getPromptText() { return this.promptText; }
    public void setPromptText(String promptText) { this.promptText = promptText; }
    public Integer getTargetQuestionCount() { return this.targetQuestionCount; }
    public void setTargetQuestionCount(Integer targetQuestionCount) { this.targetQuestionCount = targetQuestionCount; }
    public Integer getCurrentBatchNo() { return this.currentBatchNo; }
    public void setCurrentBatchNo(Integer currentBatchNo) { this.currentBatchNo = currentBatchNo; }
    public Integer getTotalBatchCount() { return this.totalBatchCount; }
    public void setTotalBatchCount(Integer totalBatchCount) { this.totalBatchCount = totalBatchCount; }
    public Integer getCompletedBatchCount() { return this.completedBatchCount; }
    public void setCompletedBatchCount(Integer completedBatchCount) { this.completedBatchCount = completedBatchCount; }
    public Integer getGeneratedQuestionCount() { return this.generatedQuestionCount; }
    public void setGeneratedQuestionCount(Integer generatedQuestionCount) { this.generatedQuestionCount = generatedQuestionCount; }
    public Integer getRetryCount() { return this.retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getDimensionGeneratedFlag() { return this.dimensionGeneratedFlag; }
    public void setDimensionGeneratedFlag(Integer dimensionGeneratedFlag) { this.dimensionGeneratedFlag = dimensionGeneratedFlag; }
    public Integer getResultRuleGeneratedFlag() { return this.resultRuleGeneratedFlag; }
    public void setResultRuleGeneratedFlag(Integer resultRuleGeneratedFlag) { this.resultRuleGeneratedFlag = resultRuleGeneratedFlag; }
    public Integer getTaskStatus() { return this.taskStatus; }
    public void setTaskStatus(Integer taskStatus) { this.taskStatus = taskStatus; }
    public String getErrorMessage() { return this.errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCompletedAt() { return this.completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getSubmittedAt() { return this.submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
