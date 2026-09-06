package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 答卷题目快照实体，对应 t_answer_session_question。
 */
@TableName("t_answer_session_question")
public class AnswerSessionQuestionEntity extends BaseAssessmentEntity {

    /** 答题会话 ID。 */
    private Long answerSessionId;
    /** 题目 ID。 */
    private Long questionId;
    /** 本次答题顺序。 */
    private Integer sortNo;

    public Long getAnswerSessionId() { return this.answerSessionId; }
    public void setAnswerSessionId(Long answerSessionId) { this.answerSessionId = answerSessionId; }
    public Long getQuestionId() { return this.questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Integer getSortNo() { return this.sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
