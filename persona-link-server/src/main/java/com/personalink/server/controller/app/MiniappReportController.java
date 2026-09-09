package com.personalink.server.controller.app;

import lombok.RequiredArgsConstructor;

import com.personalink.server.dto.ReportHistoryResponse;
import com.personalink.server.dto.ReportResponse;
import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.PageResponse;
import com.personalink.server.service.AssessmentService;
import com.personalink.server.service.impl.AuthService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序个人报告接口。
 */
@Validated
@RestController
@RequestMapping("/api/miniapp/reports")
@RequiredArgsConstructor
public class MiniappReportController {

    private final AuthService authService;
    private final AssessmentService assessmentService;

    /**
     * 查询当前用户的个人报告详情。
     *
     * @param authorization Bearer 业务会话令牌
     * @param reportId 报告 ID
     * @return 报告生成时的固定快照
     */
    @GetMapping("/{reportId}")
    public ApiResponse<ReportResponse> detail(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long reportId) {
        String openId = this.authService.requireSession(authorization).openId();
        return ApiResponse.success(this.assessmentService.getReport(openId, reportId));
    }

    /**
     * 分页查询当前用户的个人报告历史。
     *
     * @param authorization Bearer 业务会话令牌
     * @param page 页码，从 1 开始
     * @param size 每页数量，最大 100
     * @return 历史报告分页数据
     */
    @GetMapping
    public ApiResponse<PageResponse<ReportHistoryResponse>> history(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size) {
        String openId = this.authService.requireSession(authorization).openId();
        return ApiResponse.success(this.assessmentService.history(openId, page, size));
    }

    /**
     * 逻辑删除当前用户的个人报告。
     *
     * @param authorization Bearer 业务会话令牌
     * @param reportId 报告 ID
     * @return 空响应
     */
    @DeleteMapping("/{reportId}")
    public ApiResponse<Void> delete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long reportId) {
        String openId = this.authService.requireSession(authorization).openId();
        this.assessmentService.deleteReport(openId, reportId);
        return ApiResponse.success(null);
    }
}
