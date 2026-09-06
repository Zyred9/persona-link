package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/** 题目实体，对应 t_question。 */
@TableName("t_question")
public class QuestionEntity extends BaseAssessmentEntity {
    /** 版本 ID。 */
    private Long versionId;
    /** 题型：1单选，2多选。 */
    private Integer questionType;
    /** 单选题计分维度 ID。 */
    private Long dimensionId;
    /** 最少选择数。 */
    private Integer minSelectCount;
    /** 最多选择数。 */
    private Integer maxSelectCount;
    /** 题号。 */
    private Integer questionNo;
    /** 题干。 */
    private String questionText;
    /** 是否必答。 */
    private Integer requiredFlag;
    /** 排序值。 */
    private Integer sortNo;
    public Long getVersionId() { return this.versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Integer getQuestionType() { return this.questionType; }
    public void setQuestionType(Integer questionType) { this.questionType = questionType; }
    public Long getDimensionId() { return this.dimensionId; }
    public void setDimensionId(Long dimensionId) { this.dimensionId = dimensionId; }
    public Integer getMinSelectCount() { return this.minSelectCount; }
    public void setMinSelectCount(Integer minSelectCount) { this.minSelectCount = minSelectCount; }
    public Integer getMaxSelectCount() { return this.maxSelectCount; }
    public void setMaxSelectCount(Integer maxSelectCount) { this.maxSelectCount = maxSelectCount; }
    public Integer getQuestionNo() { return this.questionNo; }
    public void setQuestionNo(Integer questionNo) { this.questionNo = questionNo; }
    public String getQuestionText() { return this.questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public Integer getRequiredFlag() { return this.requiredFlag; }
    public void setRequiredFlag(Integer requiredFlag) { this.requiredFlag = requiredFlag; }
    public Integer getSortNo() { return this.sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
