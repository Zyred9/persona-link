package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 答卷选项明细实体，对应 t_answer_detail。
 */
@TableName("t_answer_detail")
public class AnswerDetailEntity extends BaseAssessmentEntity {

    /** 答题会话 ID。 */
    private Long answerSessionId;
    /** 题目 ID。 */
    private Long questionId;
    /** 选项 ID。 */
    private Long optionId;

    public Long getAnswerSessionId() { return this.answerSessionId; }
    public void setAnswerSessionId(Long answerSessionId) { this.answerSessionId = answerSessionId; }
    public Long getQuestionId() { return this.questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Long getOptionId() { return this.optionId; }
    public void setOptionId(Long optionId) { this.optionId = optionId; }
}
