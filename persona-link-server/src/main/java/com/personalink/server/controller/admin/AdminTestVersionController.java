package com.personalink.server.controller.admin;

import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.PublishCheckResponse;
import com.personalink.server.dto.QuestionResponse;
import com.personalink.server.dto.QuestionSaveRequest;
import com.personalink.server.dto.ResultConfigResponse;
import com.personalink.server.dto.ResultConfigSaveRequest;
import com.personalink.server.dto.TestVersionResponse;
import com.personalink.server.dto.TestVersionSaveRequest;
import com.personalink.server.dto.VersionScheduleRequest;
import com.personalink.server.dto.AdminSessionContext;
import com.personalink.server.interceptor.AdminAuthInterceptor;
import com.personalink.server.service.ContentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台题型版本内容与发布接口。 */
@Validated
@RestController
@RequestMapping("/api/admin/test-versions")
public class AdminTestVersionController {

    private final ContentService contentService;

    public AdminTestVersionController(ContentService contentService) {
        this.contentService = contentService;
    }

    /**
     * 查询版本详情。
     *
     * @param id 版本 ID
     * @return 版本详情
     */
    @GetMapping("/{id}")
    public ApiResponse<TestVersionResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(this.contentService.getVersion(id));
    }

    /**
     * 更新草稿版本及计分维度。
     *
     * @param id 版本 ID
     * @param request 版本参数
     * @param servletRequest HTTP 请求
     * @return 更新后的版本
     */
    @PutMapping("/{id}")
    public ApiResponse<TestVersionResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody TestVersionSaveRequest request,
                                                   HttpServletRequest servletRequest) {
        return ApiResponse.success(this.contentService.updateVersion(
                id, request, this.operatorId(servletRequest)));
    }

    /**
     * 将已发布或已下线版本的全部内容复制为新草稿。
     *
     * @param id 来源版本 ID
     * @param servletRequest HTTP 请求
     * @return 新草稿版本
     */
    @PostMapping("/{id}/draft-copy")
    public ApiResponse<TestVersionResponse> copyAsDraft(@PathVariable Long id,
                                                        HttpServletRequest servletRequest) {
        return ApiResponse.success(this.contentService.copyVersionAsDraft(
                id, this.operatorId(servletRequest)));
    }

    /**
     * 查询版本题目和选项列表。
     *
     * @param id 版本 ID
     * @return 题目列表
     */
    @GetMapping("/{id}/questions")
    public ApiResponse<List<QuestionResponse>> questions(@PathVariable Long id) {
        return ApiResponse.success(this.contentService.listQuestions(id));
    }

    /**
     * 新增题目并批量保存选项。
     *
     * @param id 版本 ID
     * @param request 题目参数
     * @param servletRequest HTTP 请求
     * @return 新增后的题目
     */
    @PostMapping("/{id}/questions")
    public ApiResponse<QuestionResponse> createQuestion(@PathVariable Long id,
                                                        @Valid @RequestBody QuestionSaveRequest request,
                                                        HttpServletRequest servletRequest) {
        return ApiResponse.success(this.contentService.createQuestion(
                id, request, this.operatorId(servletRequest)));
    }

    /**
     * 读取版本的维度与结果配置。
     *
     * @param id 版本 ID
     * @return 结果配置
     */
    @GetMapping("/{id}/results")
    public ApiResponse<ResultConfigResponse> results(@PathVariable Long id) {
        return ApiResponse.success(this.contentService.getResultConfig(id));
    }

    /**
     * 整体保存版本结果配置并校验每个维度连续覆盖 0-100。
     *
     * @param id 版本 ID
     * @param request 结果配置
     * @param servletRequest HTTP 请求
     * @return 保存后的结果配置
     */
    @PutMapping("/{id}/results")
    public ApiResponse<ResultConfigResponse> saveResults(@PathVariable Long id,
                                                         @Valid @RequestBody ResultConfigSaveRequest request,
                                                         HttpServletRequest servletRequest) {
        return ApiResponse.success(this.contentService.saveResultConfig(
                id, request, this.operatorId(servletRequest)));
    }

    /**
     * 执行版本发布前完整性检查。
     *
     * @param id 版本 ID
     * @return 检查结果和错误列表
     */
    @GetMapping("/{id}/publish-check")
    public ApiResponse<PublishCheckResponse> publishCheck(@PathVariable Long id) {
        return ApiResponse.success(this.contentService.checkPublish(id));
    }

    /**
     * 设置草稿版本的计划发布时间。
     *
     * @param id 版本 ID
     * @param request 排期参数
     * @param servletRequest HTTP 请求
     * @return 待发布版本
     */
    @PostMapping("/{id}/schedule")
    public ApiResponse<TestVersionResponse> schedule(@PathVariable Long id,
                                                     @Valid @RequestBody VersionScheduleRequest request,
                                                     HttpServletRequest servletRequest) {
        return ApiResponse.success(this.contentService.schedule(
                id, request.scheduledAt(), this.operatorId(servletRequest)));
    }

    /**
     * 取消版本排期并恢复为草稿。
     *
     * @param id 版本 ID
     * @param servletRequest HTTP 请求
     * @return 草稿版本
     */
    @PostMapping("/{id}/schedule/cancel")
    public ApiResponse<TestVersionResponse> cancelSchedule(@PathVariable Long id,
                                                           HttpServletRequest servletRequest) {
        return ApiResponse.success(this.contentService.cancelSchedule(
                id, this.operatorId(servletRequest)));
    }

    /**
     * 立即发布版本，并在同一事务中下线原发布版本。
     *
     * @param id 版本 ID
     * @param servletRequest HTTP 请求
     * @return 发布后的版本
     */
    @PostMapping("/{id}/publish")
    public ApiResponse<TestVersionResponse> publish(@PathVariable Long id,
                                                    HttpServletRequest servletRequest) {
        return ApiResponse.success(this.contentService.publish(id, this.operatorId(servletRequest)));
    }

    /**
     * 下线当前发布版本。
     *
     * @param id 版本 ID
     * @param reason 下线原因
     * @param servletRequest HTTP 请求
     * @return 下线后的版本
     */
    @PostMapping("/{id}/offline")
    public ApiResponse<TestVersionResponse> offline(@PathVariable Long id,
                                                    @RequestParam(required = false) String reason,
                                                    HttpServletRequest servletRequest) {
        return ApiResponse.success(this.contentService.offline(
                id, reason, this.operatorId(servletRequest)));
    }

    /**
     * 归档已下线版本。
     *
     * @param id 版本 ID
     * @param servletRequest HTTP 请求
     * @return 已归档版本
     */
    @PostMapping("/{id}/archive")
    public ApiResponse<TestVersionResponse> archive(@PathVariable Long id,
                                                    HttpServletRequest servletRequest) {
        return ApiResponse.success(this.contentService.archive(id, this.operatorId(servletRequest)));
    }

    private Long operatorId(HttpServletRequest request) {
        AdminSessionContext context = (AdminSessionContext) request.getAttribute(
                AdminAuthInterceptor.SESSION_CONTEXT_ATTRIBUTE);
        return context.adminAccount().getId();
    }
}
