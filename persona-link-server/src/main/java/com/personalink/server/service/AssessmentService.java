package com.personalink.server.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.dto.AssessmentSessionResponse;
import com.personalink.server.dto.AssessmentReviewResponse;
import com.personalink.server.dto.CreateAssessmentRequest;
import com.personalink.server.dto.ReportHistoryResponse;
import com.personalink.server.dto.ReportResponse;
import com.personalink.server.dto.RestartAssessmentRequest;
import com.personalink.server.dto.SaveAnswerRequest;
import com.personalink.server.dto.SubmitAssessmentRequest;
import com.personalink.server.entity.AnswerSessionEntity;
import com.personalink.server.dto.PageResponse;

/**
 * 小程序答卷与单人报告业务接口。
 */
public interface AssessmentService extends IService<AnswerSessionEntity> {

    AssessmentSessionResponse current(String openId);

    AssessmentSessionResponse create(String openId, CreateAssessmentRequest request);

    AssessmentSessionResponse resume(String openId, Long answerSessionId);

    AssessmentReviewResponse review(String openId, Long answerSessionId);

    AssessmentReviewResponse reviewPair(String openId, Long pairSessionId);

    void saveAnswer(String openId, Long answerSessionId, SaveAnswerRequest request);

    AssessmentSessionResponse restart(String openId, Long answerSessionId, RestartAssessmentRequest request);

    void abandon(String openId, Long answerSessionId);

    ReportResponse submit(String openId, Long answerSessionId, SubmitAssessmentRequest request);

    ReportResponse getReport(String openId, Long reportId);

    PageResponse<ReportHistoryResponse> history(String openId, long page, long size);

    void deleteReport(String openId, Long reportId);
}
