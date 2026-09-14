package com.personalink.server.controller.app;
import lombok.RequiredArgsConstructor;
import com.personalink.server.dto.*;
import com.personalink.server.service.*;
import com.personalink.server.service.impl.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
/** 用户反馈、公开协议和本人测试记录清理。 */
@RestController @RequestMapping("/api/miniapp") @RequiredArgsConstructor
public class MiniappUserContentController {
    private final AuthService authService;
    private final FeedbackService feedbackService;
    private final LegalDocumentService legalDocumentService;
    private final TestRecordService testRecordService;
    private final MiniappAccountService miniappAccountService;
    /**
     * 无需登录查询当前协议版本，供启动及进入核心功能前核对同意记录。
     * @return 已配置协议的类型与版本列表；缺少类型表示对应协议尚未配置
     */
    @GetMapping("/legal-documents/versions")
    public ApiResponse<java.util.List<LegalDocumentVersionResponse>> versions() {
        return ApiResponse.success(this.legalDocumentService.versions());
    }

    /**
     * 提交反馈，支持匿名：未登录不创建账号，仅在有会话时记录提交人。
     * @param authorization 可选登录令牌
     * @param request 反馈内容和幂等请求号
     * @return 收件记录
     */
    @PostMapping("/feedbacks")
    public ApiResponse<FeedbackResponse> feedback(@RequestHeader(value=HttpHeaders.AUTHORIZATION, required=false) String authorization,
            @Valid @RequestBody FeedbackCreateRequest request) {
        MiniappSessionContext session = this.authService.findSession(authorization);
        return ApiResponse.success(this.feedbackService.submit(Objects.isNull(session) ? null : session.openId(), request));
    }
    /**
     * 无需登录查询已发布协议，供首次同意前阅读。
     * @param type 协议类型：1用户协议，2隐私指引，3免责声明
     * @return 已发布的纯文本协议
     */
    @GetMapping("/legal-documents/{type}")
    public ApiResponse<LegalDocumentResponse> document(@PathVariable int type) {
        return ApiResponse.success(this.legalDocumentService.published(type));
    }
    /**
     * 删除当前用户的全部测试记录，不注销账号、不删除好友个人答卷。
     * @param authorization 登录令牌
     * @return 空响应，事务完成后返回
     */
    @DeleteMapping("/me/test-records")
    public ApiResponse<Void> deleteRecords(@RequestHeader(value=HttpHeaders.AUTHORIZATION, required=false) String authorization) {
        this.testRecordService.deleteAll(this.authService.requireSession(authorization).openId());
        return ApiResponse.success(null);
    }
    /**
     * 注销当前用户账号：删除账号资料、测试记录、反馈与埋点，并失效全部会话。
     * @param authorization 登录令牌
     * @return 空响应，事务完成后返回
     */
    @DeleteMapping("/me")
    public ApiResponse<Void> cancelAccount(@RequestHeader(value=HttpHeaders.AUTHORIZATION, required=false) String authorization) {
        this.miniappAccountService.cancel(this.authService.requireSession(authorization).openId());
        return ApiResponse.success(null);
    }
}
