package com.personalink.server.controller.admin;

import lombok.RequiredArgsConstructor;

import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.PageResponse;
import com.personalink.server.dto.AuditLogQuery;
import com.personalink.server.dto.AuditLogResponse;
import com.personalink.server.service.ContentService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台内容审计查询接口。 */
@Validated
@RestController
@RequestMapping("/api/admin/content-audits")
@RequiredArgsConstructor
public class AdminContentAuditController {

    private final ContentService contentService;

    /**
     * 分页查询内容操作审计记录。
     *
     * @param query 查询参数
     * @return 审计分页数据
     */
    @GetMapping
    public ApiResponse<PageResponse<AuditLogResponse>> page(@Valid AuditLogQuery query) {
        return ApiResponse.success(this.contentService.pageAudits(query));
    }
}
