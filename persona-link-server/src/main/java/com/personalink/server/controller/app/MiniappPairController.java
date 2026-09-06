package com.personalink.server.controller.app;

import com.personalink.server.dto.CreatePairRequest;
import com.personalink.server.dto.JoinPairRequest;
import com.personalink.server.dto.PairCreateResponse;
import com.personalink.server.dto.PairReportResponse;
import com.personalink.server.dto.PairSessionResponse;
import com.personalink.server.dto.ApiResponse;
import com.personalink.server.service.PairAssessmentService;
import com.personalink.server.service.impl.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序双人评测接口。
 */
@RestController
@RequestMapping("/api/miniapp/pairs")
public class MiniappPairController {

    private final AuthService authService;
    private final PairAssessmentService pairAssessmentService;

    public MiniappPairController(
            AuthService authService,
            PairAssessmentService pairAssessmentService) {
        this.authService = authService;
        this.pairAssessmentService = pairAssessmentService;
    }

    /**
     * 发起者完成双人答卷后创建邀请。
     *
     * @param authorization Bearer 业务会话令牌
     * @param request 发起者答卷 ID 和创建幂等请求号
     * @return 配对状态与仅本次返回的邀请令牌
     */
    @PostMapping
    public ApiResponse<PairCreateResponse> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody CreatePairRequest request) {
        String openId = this.authService.requireSession(authorization).openId();
        return ApiResponse.success(this.pairAssessmentService.create(openId, request));
    }

    /**
     * 受邀者使用邀请令牌原子加入并复制发起者题目快照。
     *
     * @param authorization Bearer 业务会话令牌
     * @param request 邀请令牌与受邀答卷创建幂等请求号
     * @return 配对状态及受邀者答卷 ID
     */
    @PostMapping("/join")
    public ApiResponse<PairSessionResponse> join(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody JoinPairRequest request) {
        String openId = this.authService.requireSession(authorization).openId();
        return ApiResponse.success(this.pairAssessmentService.join(openId, request));
    }

    /**
     * 查询当前参与者可访问的配对状态。
     *
     * @param authorization Bearer 业务会话令牌
     * @param pairSessionId 配对会话 ID
     * @return 配对状态，不包含对方逐题答案
     */
    @GetMapping("/{pairSessionId}")
    public ApiResponse<PairSessionResponse> status(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long pairSessionId) {
        String openId = this.authService.requireSession(authorization).openId();
        return ApiResponse.success(this.pairAssessmentService.getStatus(openId, pairSessionId));
    }

    /**
     * 取消尚未完成的双人配对。
     *
     * @param authorization Bearer 业务会话令牌
     * @param pairSessionId 配对会话 ID
     * @return 空响应
     */
    @PostMapping("/{pairSessionId}/cancel")
    public ApiResponse<Void> cancel(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long pairSessionId) {
        String openId = this.authService.requireSession(authorization).openId();
        this.pairAssessmentService.cancel(openId, pairSessionId);
        return ApiResponse.success(null);
    }

    /**
     * 查询双方完成后生成的基础聚合报告。
     *
     * @param authorization Bearer 业务会话令牌
     * @param pairSessionId 配对会话 ID
     * @return 双人报告快照，不包含双方逐题答案
     */
    @GetMapping("/{pairSessionId}/report")
    public ApiResponse<PairReportResponse> report(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long pairSessionId) {
        String openId = this.authService.requireSession(authorization).openId();
        return ApiResponse.success(this.pairAssessmentService.getReport(openId, pairSessionId));
    }
}
