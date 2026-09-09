package com.personalink.server.controller.admin;
import lombok.RequiredArgsConstructor;
import com.personalink.server.dto.*;
import com.personalink.server.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
/** 管理员专用的反馈列表和协议发布配置。 */
@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor @Validated
public class AdminUserContentController {
    private final FeedbackService feedbackService;
    private final LegalDocumentService legalDocumentService;
    /**
     * 分页查询用户反馈。
     * @param page 页码
     * @param size 每页数量
     * @return 按提交时间倒序的反馈列表
     */
    @GetMapping("/feedbacks")
    public ApiResponse<PageResponse<AdminFeedbackResponse>> feedbacks(@RequestParam(defaultValue="1") @Min(1) @Max(2147483647) long page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) long size) {
        return ApiResponse.success(this.feedbackService.history(page, size));
    }
    /**
     * 查询三个固定协议的配置，未配置正文为空。
     * @return 协议配置列表
     */
    @GetMapping("/legal-documents")
    public ApiResponse<List<LegalDocumentResponse>> documents() {
        return ApiResponse.success(this.legalDocumentService.documents());
    }
    /**
     * 保存并立即发布协议。
     * @param type 协议类型
     * @param request 标题及纯文本正文
     * @return 已发布协议及递增版本号
     */
    @PutMapping("/legal-documents/{type}")
    public ApiResponse<LegalDocumentResponse> publish(@PathVariable int type, @Valid @RequestBody LegalDocumentSaveRequest request) {
        return ApiResponse.success(this.legalDocumentService.publish(type, request));
    }
}
