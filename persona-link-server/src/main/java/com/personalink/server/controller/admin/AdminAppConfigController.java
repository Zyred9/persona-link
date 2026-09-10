package com.personalink.server.controller.admin;

import com.personalink.server.dto.*;
import com.personalink.server.entity.AppConfigEntity;
import com.personalink.server.service.AppConfigService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 管理员专属通用配置管理。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/app-configs")
public class AdminAppConfigController {
    private final AppConfigService service;

    /**
     * 分页查询通用配置。
     * @param query 分页和关键字
     * @return 配置分页
     */
    @GetMapping
    public ApiResponse<PageResponse<AppConfigEntity>> page(@Valid AppConfigQuery query) {
        return ApiResponse.success(this.service.pageConfigs(query));
    }

    /**
     * 查询配置详情。
     * @param id 配置ID
     * @return 配置详情
     */
    @GetMapping("/{id}")
    public ApiResponse<AppConfigEntity> detail(@PathVariable @Positive Long id) {
        return ApiResponse.success(this.service.getDetail(id));
    }

    /**
     * 新增配置，已删除的同键配置将恢复。
     * @param request 配置内容
     * @return 已保存配置
     */
    @PostMapping
    public ApiResponse<AppConfigEntity> create(@Valid @RequestBody AppConfigSaveRequest request) {
        return ApiResponse.success(this.service.create(request));
    }

    /**
     * 更新配置内容，配置键不可修改。
     * @param id 配置ID
     * @param request 配置内容
     * @return 已保存配置
     */
    @PutMapping("/{id}")
    public ApiResponse<AppConfigEntity> update(@PathVariable @Positive Long id,
                                              @Valid @RequestBody AppConfigSaveRequest request) {
        return ApiResponse.success(this.service.update(id, request));
    }

    /**
     * 批量软删除配置。
     * @param ids 配置ID列表
     * @return 空响应
     */
    @PostMapping("/deletes")
    public ApiResponse<Void> deletes(@RequestBody @NotEmpty List<@NotNull @Positive Long> ids) {
        this.service.deleteByIds(ids);
        return ApiResponse.success(null);
    }
}
