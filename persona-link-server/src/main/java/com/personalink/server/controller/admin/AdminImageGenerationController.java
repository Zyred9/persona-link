package com.personalink.server.controller.admin;

import lombok.RequiredArgsConstructor;

import com.personalink.server.dto.AdminSessionContext;
import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.ImageGenerationStartRequest;
import com.personalink.server.dto.ImageGenerationTaskResponse;
import com.personalink.server.interceptor.AdminAuthInterceptor;
import com.personalink.server.service.AiImageTaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 后台题型封面、详情双图生成接口。 */
@Validated
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminImageGenerationController {
    private final AiImageTaskService service;
    /**
     * 创建异步双图任务；通过 detail 接口继续查询进度。
     * @param testId 题型 ID
     * @param request 关键词及幂等编号
     * @param servletRequest 登录上下文
     * @return 创建的任务，不等待图片完成
     */
    @PostMapping("/tests/{testId}/image-tasks")
    public ApiResponse<ImageGenerationTaskResponse> create(@PathVariable @Positive Long testId,
            @Valid @RequestBody ImageGenerationStartRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success(this.service.create(testId, request, this.operatorId(servletRequest)));
    }

    /**
     * 查询题型最近一次生图任务。
     * @param testId 题型 ID
     * @return 最近任务，没有任务时为 null
     */
    @GetMapping("/tests/{testId}/image-tasks/latest")
    public ApiResponse<ImageGenerationTaskResponse> latest(@PathVariable @Positive Long testId) {
        return ApiResponse.success(this.service.latest(testId));
    }

    /**
     * 查询异步生图任务状态。
     * @param id 任务 ID
     * @return 任务状态及生成图片
     */
    @GetMapping("/image-tasks/{id}")
    public ApiResponse<ImageGenerationTaskResponse> detail(@PathVariable @Positive Long id) {
        return ApiResponse.success(this.service.get(id));
    }

    /**
     * 重试失败任务的缺失图片；通过 detail 接口继续查询进度。
     * @param id 任务 ID
     * @return 重新进入队列的任务
     */
    @PostMapping("/image-tasks/{id}/retry")
    public ApiResponse<ImageGenerationTaskResponse> retry(@PathVariable @Positive Long id) {
        return ApiResponse.success(this.service.retry(id));
    }

    /**
     * 将两张图片采用到草稿，不自动发布。
     * @param id 任务 ID
     * @param servletRequest 登录上下文
     * @return 已采用任务及草稿版本 ID
     */
    @PostMapping("/image-tasks/{id}/apply")
    public ApiResponse<ImageGenerationTaskResponse> apply(@PathVariable @Positive Long id,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(this.service.apply(id, this.operatorId(servletRequest)));
    }

    private Long operatorId(HttpServletRequest request) {
        AdminSessionContext context = (AdminSessionContext) request.getAttribute(AdminAuthInterceptor.SESSION_CONTEXT_ATTRIBUTE);
        return context.adminAccount().getId();
    }
}
