package com.personalink.server.controller.app;

import lombok.RequiredArgsConstructor;
import com.personalink.server.dto.*;
import com.personalink.server.enums.ReportKind;
import com.personalink.server.service.ReportAccessService;
import com.personalink.server.service.impl.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

/** 单人及双人报告访问授权；身份只来自业务会话。 */
@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
public class MiniappReportAccessController {
    private final AuthService authService;
    private final ReportAccessService accessService;
    /** 查询单人报告权限，必要时创建广告任务。
     * @param authorization 业务会话
     * @param reportId 单人报告 ID
     * @return 权限及广告任务，完成后调用 ad-result
     */
    @GetMapping("/reports/{reportId}/access")
    public ApiResponse<ReportAccessResponse> single(
            @RequestHeader(value=HttpHeaders.AUTHORIZATION, required=false) String authorization,
            @PathVariable Long reportId) {
        return ApiResponse.success(this.accessService.access(this.authService.requireSession(authorization).openId(),
                ReportKind.SINGLE.getCode(), reportId));
    }

    /** 提交单人广告事件；授权成功后查询原报告详情接口。
     * @param authorization 业务会话
     * @param reportId 单人报告 ID
     * @param request 广告任务事件
     * @return 最新访问状态
     */
    @PostMapping("/reports/{reportId}/ad-result")
    public ApiResponse<ReportAccessResponse> singleResult(
            @RequestHeader(value=HttpHeaders.AUTHORIZATION, required=false) String authorization,
            @PathVariable Long reportId, @Valid @RequestBody AdResultRequest request) {
        return ApiResponse.success(this.accessService.submitAd(this.authService.requireSession(authorization).openId(),
                ReportKind.SINGLE.getCode(), reportId, request));
    }

    /** 查询双人报告当前参与者权限，不为另一参与者授权。
     * @param authorization 业务会话
     * @param pairSessionId 配对会话 ID
     * @return 权限及广告任务，完成后调用 ad-result
     */
    @GetMapping("/pairs/{pairSessionId}/report/access")
    public ApiResponse<ReportAccessResponse> pair(
            @RequestHeader(value=HttpHeaders.AUTHORIZATION, required=false) String authorization,
            @PathVariable Long pairSessionId) {
        return ApiResponse.success(this.accessService.access(this.authService.requireSession(authorization).openId(),
                ReportKind.PAIR.getCode(), pairSessionId));
    }

    /** 提交双人广告事件；成功后查询原双人报告接口。
     * @param authorization 业务会话
     * @param pairSessionId 配对会话 ID
     * @param request 广告任务事件
     * @return 当前参与者最新访问状态
     */
    @PostMapping("/pairs/{pairSessionId}/report/ad-result")
    public ApiResponse<ReportAccessResponse> pairResult(
            @RequestHeader(value=HttpHeaders.AUTHORIZATION, required=false) String authorization,
            @PathVariable Long pairSessionId, @Valid @RequestBody AdResultRequest request) {
        return ApiResponse.success(this.accessService.submitAd(this.authService.requireSession(authorization).openId(),
                ReportKind.PAIR.getCode(), pairSessionId, request));
    }
}
