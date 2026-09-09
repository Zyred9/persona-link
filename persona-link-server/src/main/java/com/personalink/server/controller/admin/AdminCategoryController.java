package com.personalink.server.controller.admin;

import lombok.RequiredArgsConstructor;

import com.personalink.server.dto.CategoryOrderRequest;
import com.personalink.server.dto.CategoryQuery;
import com.personalink.server.dto.CategoryResponse;
import com.personalink.server.dto.CategorySaveRequest;
import com.personalink.server.dto.ApiResponse;
import com.personalink.server.dto.PageResponse;
import com.personalink.server.service.CategoryService;
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

/**
 * 后台分类管理接口。
 */
@Validated
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    /**
     * 分页查询分类。
     *
     * @param query 查询参数
     * @return 分类分页数据
     */
    @GetMapping
    public ApiResponse<PageResponse<CategoryResponse>> page(@Valid CategoryQuery query) {
        return ApiResponse.success(this.categoryService.page(query));
    }

    /**
     * 查询分类详情。
     *
     * @param id 分类 ID
     * @return 分类详情
     */
    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(this.categoryService.getDetail(id));
    }

    /**
     * 新增分类。
     *
     * @param request 分类参数
     * @return 新增后的分类
     */
    @PostMapping
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategorySaveRequest request) {
        return ApiResponse.success(this.categoryService.create(request));
    }

    /**
     * 更新分类。
     *
     * @param id 分类 ID
     * @param request 分类参数
     * @return 更新后的分类
     */
    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CategorySaveRequest request) {
        return ApiResponse.success(this.categoryService.update(id, request));
    }

    /**
     * 批量逻辑删除分类。
     *
     * @param ids 分类 ID 列表
     * @return 空响应
     */
    @PostMapping("/deletes")
    public ApiResponse<Void> deleteByIds(
            @RequestBody @NotEmpty List<@NotNull @Positive Long> ids) {
        this.categoryService.deleteByIds(ids);
        return ApiResponse.success(null);
    }

    /**
     * 更新分类展示顺序。
     *
     * @param request 排序参数
     * @return 空响应
     */
    @PutMapping("/order")
    public ApiResponse<Void> updateOrder(@Valid @RequestBody CategoryOrderRequest request) {
        this.categoryService.updateOrder(request.orderedIds());
        return ApiResponse.success(null);
    }
}
