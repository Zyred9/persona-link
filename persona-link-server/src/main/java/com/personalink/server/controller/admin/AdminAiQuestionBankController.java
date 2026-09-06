package com.personalink.server.controller.admin;

import com.personalink.server.dto.AdminSessionContext;
import com.personalink.server.dto.AiGenerationStartRequest;
import com.personalink.server.dto.AiGenerationTaskResponse;
import com.personalink.server.dto.AiGenerationTaskQuery;
import com.personalink.server.dto.AiGenerationTaskSummaryResponse;
import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.PageResponse;
import com.personalink.server.interceptor.AdminAuthInterceptor;
import com.personalink.server.service.AiQuestionBankService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台 AI 题库助手接口。 */
@Validated
@RestController
@RequestMapping("/api/admin/ai-question-bank/tasks")
public class AdminAiQuestionBankController {

    private final AiQuestionBankService aiQuestionBankService;

    public AdminAiQuestionBankController(AiQuestionBankService aiQuestionBankService) {
        this.aiQuestionBankService = aiQuestionBankService;
    }

    /**
     * 分页查询 AI 题库生成任务。
     *
     * @param query 查询参数
     * @return 任务分页数据
     */
    @GetMapping
    public ApiResponse<PageResponse<AiGenerationTaskSummaryResponse>> page(
            @Valid AiGenerationTaskQuery query) {
        return ApiResponse.success(this.aiQuestionBankService.page(query));
    }

    /**
     * 创建题型、草稿版本和异步 AI 生成任务。
     * <p>调用方通过 {@link #detail(Long)} 轮询生成进度。</p>
     *
     * @param request 生成参数
     * @param servletRequest HTTP 请求
     * @return 已创建或幂等命中的任务
     */
    @PostMapping
    public ApiResponse<AiGenerationTaskResponse> create(
            @Valid @RequestBody AiGenerationStartRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(this.aiQuestionBankService.create(
                request, this.operatorId(servletRequest)));
    }

    /**
     * 查询 AI 生成任务真实进度。
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @GetMapping("/{id}")
    public ApiResponse<AiGenerationTaskResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(this.aiQuestionBankService.get(id));
    }

    /**
     * 从失败任务的下一未完成批次继续生成。
     * <p>调用方通过 {@link #detail(Long)} 继续轮询生成进度。</p>
     *
     * @param id 任务 ID
     * @return 已重新进入队列的任务
     */
    @PostMapping("/{id}/retry")
    public ApiResponse<AiGenerationTaskResponse> retry(@PathVariable Long id) {
        return ApiResponse.success(this.aiQuestionBankService.retry(id));
    }

    /**
     * 完整性校验通过后提交到题型库，版本仍保持草稿且不会进入首页。
     *
     * @param id 任务 ID
     * @return 已提交任务
     */
    @PostMapping("/{id}/submit")
    public ApiResponse<AiGenerationTaskResponse> submit(@PathVariable Long id) {
        return ApiResponse.success(this.aiQuestionBankService.submit(id));
    }

    private Long operatorId(HttpServletRequest request) {
        AdminSessionContext context = (AdminSessionContext) request.getAttribute(
                AdminAuthInterceptor.SESSION_CONTEXT_ATTRIBUTE);
        return context.adminAccount().getId();
    }
}
