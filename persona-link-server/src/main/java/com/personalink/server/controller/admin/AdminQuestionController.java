package com.personalink.server.controller.admin;

import lombok.RequiredArgsConstructor;

import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.QuestionResponse;
import com.personalink.server.dto.QuestionSaveRequest;
import com.personalink.server.dto.AdminSessionContext;
import com.personalink.server.interceptor.AdminAuthInterceptor;
import com.personalink.server.service.ContentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台题目编辑接口。 */
@Validated
@RestController
@RequestMapping("/api/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionController {

    private final ContentService contentService;

    /**
     * 更新题目并整体替换其选项。
     *
     * @param id 题目 ID
     * @param request 题目参数
     * @param servletRequest HTTP 请求
     * @return 更新后的题目
     */
    @PutMapping("/{id}")
    public ApiResponse<QuestionResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody QuestionSaveRequest request,
                                                HttpServletRequest servletRequest) {
        return ApiResponse.success(this.contentService.updateQuestion(
                id, request, this.operatorId(servletRequest)));
    }

    /**
     * 逻辑删除题目及其全部选项。
     *
     * @param id 题目 ID
     * @param servletRequest HTTP 请求
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest servletRequest) {
        this.contentService.deleteQuestion(id, this.operatorId(servletRequest));
        return ApiResponse.success(null);
    }

    private Long operatorId(HttpServletRequest request) {
        AdminSessionContext context = (AdminSessionContext) request.getAttribute(
                AdminAuthInterceptor.SESSION_CONTEXT_ATTRIBUTE);
        return context.adminAccount().getId();
    }
}
