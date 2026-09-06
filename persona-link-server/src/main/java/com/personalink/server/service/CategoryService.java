package com.personalink.server.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.dto.CategoryQuery;
import com.personalink.server.dto.CategoryResponse;
import com.personalink.server.dto.CategorySaveRequest;
import com.personalink.server.entity.CategoryEntity;
import com.personalink.server.dto.PageResponse;

import java.util.List;

/**
 * 分类业务接口。
 */
public interface CategoryService extends IService<CategoryEntity> {

    PageResponse<CategoryResponse> page(CategoryQuery query);

    CategoryResponse getDetail(Long id);

    CategoryResponse create(CategorySaveRequest request);

    CategoryResponse update(Long id, CategorySaveRequest request);

    void deleteByIds(List<Long> ids);

    void updateOrder(List<Long> orderedIds);
}
