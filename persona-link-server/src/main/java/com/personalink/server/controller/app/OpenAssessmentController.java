package com.personalink.server.controller.app;

import lombok.RequiredArgsConstructor;

import com.personalink.server.dto.AssessmentSessionResponse;
import com.personalink.server.dto.AssessmentReviewResponse;
import com.personalink.server.dto.CreateAssessmentRequest;
import com.personalink.server.dto.ReportResponse;
import com.personalink.server.dto.RestartAssessmentRequest;
import com.personalink.server.dto.SaveAnswerRequest;
import com.personalink.server.dto.SubmitAssessmentRequest;
import com.personalink.server.dto.ApiResponse;
import com.personalink.server.service.AssessmentService;
import com.personalink.server.service.impl.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序答卷接口。
 */
@RestController
@RequestMapping("/api/miniapp/assessments")
@RequiredArgsConstructor
public class OpenAssessmentController {

    private final AuthService authService;
    private final AssessmentService assessmentService;

    /**
     * 只读回顾本人已提交答卷，按原抽题顺序返回。
     * @param authorization Bearer 业务会话令牌
     * @param answerSessionId 历史答卷 ID
     * @return 本人题目及已选答案
     */
    @GetMapping("/{answerSessionId}/review")
    public ApiResponse<AssessmentReviewResponse> review(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long answerSessionId) {
        return ApiResponse.success(this.assessmentService.review(
                this.authService.requireSession(authorization).openId(), answerSessionId));
    }

    /**
     * 查询当前用户最近一份未完成答卷。
     *
     * @param authorization Bearer 业务会话令牌
     * @return 未完成答卷；没有时 data 为 null
     */
    @GetMapping("/current")
    public ApiResponse<AssessmentSessionResponse> current(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String openId = this.authService.requireSession(authorization).openId();
        return ApiResponse.success(this.assessmentService.current(openId));
    }

    /**
     * 按题型当前已发布版本创建固定题目答卷。
     *
     * @param authorization Bearer 业务会话令牌
     * @param request 题型 ID 与创建幂等请求号
     * @return 新建或幂等命中的答卷
     */
    @PostMapping
    public ApiResponse<AssessmentSessionResponse> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody CreateAssessmentRequest request) {
        String openId = this.authService.requireSession(authorization).openId();
        return ApiResponse.success(this.assessmentService.create(openId, request));
    }

    /**
     * 读取当前用户的固定题目快照与已保存答案。
     *
     * @param authorization Bearer 业务会话令牌
     * @param answerSessionId 答卷 ID
     * @return 答卷续答视图
     */
    @GetMapping("/{answerSessionId}")
    public ApiResponse<AssessmentSessionResponse> resume(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long answerSessionId) {
        String openId = this.authService.requireSession(authorization).openId();
        return ApiResponse.success(this.assessmentService.resume(openId, answerSessionId));
    }

    /**
     * 幂等替换当前答卷同一题的已选选项。
     *
     * @param authorization Bearer 业务会话令牌
     * @param answerSessionId 答卷 ID
     * @param request 题目 ID 与选项 ID 列表
     * @return 空响应
     */
    @PutMapping("/{answerSessionId}/answers")
    public ApiResponse<Void> saveAnswer(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long answerSessionId,
            @Valid @RequestBody SaveAnswerRequest request) {
        String openId = this.authService.requireSession(authorization).openId();
        this.assessmentService.saveAnswer(openId, answerSessionId, request);
        return ApiResponse.success(null);
    }

    /**
     * 放弃旧答卷并从同一题型当前已发布版本重新开始。
     *
     * @param authorization Bearer 业务会话令牌
     * @param answerSessionId 原答卷 ID
     * @param request 新答卷创建幂等请求号
     * @return 新答卷
     */
    @PostMapping("/{answerSessionId}/restart")
    public ApiResponse<AssessmentSessionResponse> restart(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long answerSessionId,
            @Valid @RequestBody RestartAssessmentRequest request) {
        String openId = this.authService.requireSession(authorization).openId();
        return ApiResponse.success(this.assessmentService.restart(openId, answerSessionId, request));
    }

    /**
     * 幂等提交答卷，并在同一事务中计分及生成个人报告。
     *
     * @param authorization Bearer 业务会话令牌
     * @param answerSessionId 答卷 ID
     * @param request 提交幂等请求号
     * @return 已生成的同一份个人报告
     */
    @PostMapping("/{answerSessionId}/submit")
    public ApiResponse<ReportResponse> submit(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long answerSessionId,
            @Valid @RequestBody SubmitAssessmentRequest request) {
        String openId = this.authService.requireSession(authorization).openId();
        return ApiResponse.success(this.assessmentService.submit(openId, answerSessionId, request));
    }

    /**
     * 幂等作废当前用户未完成的答卷，不创建新答卷。
     * @param authorization Bearer 业务会话令牌
     * @param answerSessionId 待作废答卷 ID
     * @return 空响应
     */
    @PostMapping("/{answerSessionId}/abandon")
    public ApiResponse<Void> abandon(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long answerSessionId) {
        this.assessmentService.abandon(this.authService.requireSession(authorization).openId(), answerSessionId);
        return ApiResponse.success(null);
    }
}
