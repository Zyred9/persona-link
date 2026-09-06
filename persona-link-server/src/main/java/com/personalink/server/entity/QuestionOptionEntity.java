package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/** 题目选项实体，对应 t_question_option。 */
@TableName("t_question_option")
public class QuestionOptionEntity extends BaseAssessmentEntity {
    /** 题目 ID。 */
    private Long questionId;
    /** 选项编码。 */
    private String optionCode;
    /** 选项文案。 */
    private String optionText;
    /** 多选题计分维度 ID。 */
    private Long dimensionId;
    /** 整数计分值。 */
    private Integer scoreValue;
    /** 排序值。 */
    private Integer sortNo;
    public Long getQuestionId() { return this.questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getOptionCode() { return this.optionCode; }
    public void setOptionCode(String optionCode) { this.optionCode = optionCode; }
    public String getOptionText() { return this.optionText; }
    public void setOptionText(String optionText) { this.optionText = optionText; }
    public Long getDimensionId() { return this.dimensionId; }
    public void setDimensionId(Long dimensionId) { this.dimensionId = dimensionId; }
    public Integer getScoreValue() { return this.scoreValue; }
    public void setScoreValue(Integer scoreValue) { this.scoreValue = scoreValue; }
    public Integer getSortNo() { return this.sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
