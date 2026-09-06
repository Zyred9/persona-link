package com.personalink.server.controller.admin;

import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.PageResponse;
import com.personalink.server.dto.TestQuery;
import com.personalink.server.dto.TestHomeDisplayRequest;
import com.personalink.server.dto.TestResponse;
import com.personalink.server.dto.TestSaveRequest;
import com.personalink.server.dto.TestStatusRequest;
import com.personalink.server.dto.TestVersionResponse;
import com.personalink.server.dto.TestVersionSaveRequest;
import com.personalink.server.dto.AdminSessionContext;
import com.personalink.server.interceptor.AdminAuthInterceptor;
import com.personalink.server.service.ContentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台题型管理接口。 */
@Validated
@RestController
@RequestMapping("/api/admin/tests")
public class AdminTestController {

    private final ContentService contentService;

    public AdminTestController(ContentService contentService) {
        this.contentService = contentService;
    }

    /**
     * 分页查询题型。
     *
     * @param query 查询参数
     * @return 题型分页数据
     */
    @GetMapping
    public ApiResponse<PageResponse<TestResponse>> page(@Valid TestQuery query) {
        return ApiResponse.success(this.contentService.pageTests(query));
    }

    /**
     * 查询题型详情。
     *
     * @param id 题型 ID
     * @return 题型详情
     */
    @GetMapping("/{id}")
    public ApiResponse<TestResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(this.contentService.getTest(id));
    }

    /**
     * 新增题型，题型编码由服务端生成。
     *
     * @param request 题型参数
     * @param servletRequest HTTP 请求
     * @return 新增后的题型
     */
    @PostMapping
    public ApiResponse<TestResponse> create(@Valid @RequestBody TestSaveRequest request,
                                            HttpServletRequest servletRequest) {
        return ApiResponse.success(this.contentService.createTest(request, this.operatorId(servletRequest)));
    }

    /**
     * 更新题型基础信息。
     *
     * @param id 题型 ID
     * @param request 题型参数
     * @param servletRequest HTTP 请求
     * @return 更新后的题型
     */
    @PutMapping("/{id}")
    public ApiResponse<TestResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody TestSaveRequest request,
                                            HttpServletRequest servletRequest) {
        return ApiResponse.success(this.contentService.updateTest(id, request, this.operatorId(servletRequest)));
    }

    /**
     * 更新题型启停状态。
     *
     * @param id 题型 ID
     * @param request 状态参数
     * @param servletRequest HTTP 请求
     * @return 更新后的题型
     */
    @PutMapping("/{id}/status")
    public ApiResponse<TestResponse> updateStatus(@PathVariable Long id,
                                                  @Valid @RequestBody TestStatusRequest request,
                                                  HttpServletRequest servletRequest) {
        return ApiResponse.success(this.contentService.updateTestStatus(
                id, request.status(), this.operatorId(servletRequest)));
    }

    /**
     * 更新题型在小程序首页的展示位置和顺序。
     *
     * @param id 题型 ID
     * @param request 首页展示参数
     * @param servletRequest HTTP 请求
     * @return 更新后的题型
     */
    @PutMapping("/{id}/home-display")
    public ApiResponse<TestResponse> updateHomeDisplay(@PathVariable Long id,
                                                       @Valid @RequestBody TestHomeDisplayRequest request,
                                                       HttpServletRequest servletRequest) {
        return ApiResponse.success(this.contentService.updateTestHomeDisplay(
                id, request.homeDisplay(), request.homeSort(), this.operatorId(servletRequest)));
    }

    /**
     * 批量逻辑删除题型。
     *
     * @param ids 题型 ID 列表
     * @param servletRequest HTTP 请求
     * @return 空响应
     */
    @PostMapping("/deletes")
    public ApiResponse<Void> deleteByIds(
            @RequestBody @NotEmpty List<@NotNull @Positive Long> ids,
            HttpServletRequest servletRequest) {
        this.contentService.deleteTests(ids, this.operatorId(servletRequest));
        return ApiResponse.success(null);
    }

    /**
     * 查询题型的版本列表。
     *
     * @param id 题型 ID
     * @return 版本列表
     */
    @GetMapping("/{id}/versions")
    public ApiResponse<List<TestVersionResponse>> versions(@PathVariable Long id) {
        return ApiResponse.success(this.contentService.listVersions(id));
    }

    /**
     * 创建题型草稿版本，版本号由服务端递增生成。
     *
     * @param id 题型 ID
     * @param request 版本参数
     * @param servletRequest HTTP 请求
     * @return 新增后的版本
     */
    @PostMapping("/{id}/versions")
    public ApiResponse<TestVersionResponse> createVersion(@PathVariable Long id,
                                                          @Valid @RequestBody TestVersionSaveRequest request,
                                                          HttpServletRequest servletRequest) {
        return ApiResponse.success(this.contentService.createVersion(
                id, request, this.operatorId(servletRequest)));
    }

    private Long operatorId(HttpServletRequest request) {
        AdminSessionContext context = (AdminSessionContext) request.getAttribute(
                AdminAuthInterceptor.SESSION_CONTEXT_ATTRIBUTE);
        return context.adminAccount().getId();
    }
}
