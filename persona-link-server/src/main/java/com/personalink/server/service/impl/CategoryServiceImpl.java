package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.personalink.server.dto.CategoryQuery;
import com.personalink.server.dto.CategoryResponse;
import com.personalink.server.dto.CategorySaveRequest;
import com.personalink.server.entity.CategoryEntity;
import com.personalink.server.entity.TestEntity;
import com.personalink.server.mapper.CategoryMapper;
import com.personalink.server.mapper.TestMapper;
import com.personalink.server.service.CategoryService;
import com.personalink.server.dto.PageResponse;
import com.personalink.server.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分类业务实现。
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, CategoryEntity>
        implements CategoryService {

    private static final int NORMAL = 0;
    private static final int DELETED = 1;

    private final TestMapper testMapper;

    public CategoryServiceImpl(TestMapper testMapper) {
        this.testMapper = testMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> page(CategoryQuery query) {
        long total = this.lambdaQuery()
                .like(Objects.nonNull(query.getKeyword()) && !query.getKeyword().isBlank(),
                        CategoryEntity::getCategoryName, query.getKeyword())
                .eq(Objects.nonNull(query.getStatus()), CategoryEntity::getStatus, query.getStatus())
                .eq(CategoryEntity::getDeleted, NORMAL)
                .count();
        long offset = (query.getPage() - 1) * query.getSize();
        List<CategoryResponse> records = this.baseMapper.selectCategoryPage(query, offset, query.getSize());
        return new PageResponse<>(records, total, query.getPage(), query.getSize());
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getDetail(Long id) {
        CategoryResponse response = this.baseMapper.selectCategoryById(id);
        if (Objects.isNull(response)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.value(), "分类不存在");
        }
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryResponse create(CategorySaveRequest request) {
        CategoryEntity lastCategory = this.lambdaQuery()
                .select(CategoryEntity::getSortNo)
                .eq(CategoryEntity::getDeleted, NORMAL)
                .orderByDesc(CategoryEntity::getSortNo)
                .last("LIMIT 1")
                .one();
        Integer maxSortNo = Objects.isNull(lastCategory) ? null : lastCategory.getSortNo();
        CategoryEntity entity = new CategoryEntity();
        entity.setCategoryName(request.categoryName());
        entity.setStatus(request.status());
        entity.setSortNo(Objects.isNull(maxSortNo) ? 1 : maxSortNo + 1);
        entity.setDeleted(NORMAL);
        this.save(entity);
        return this.getDetail(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryResponse update(Long id, CategorySaveRequest request) {
        if (Objects.isNull(this.getById(id))) {
            throw new BusinessException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.value(), "分类不存在");
        }
        CategoryEntity entity = new CategoryEntity();
        entity.setId(id);
        entity.setCategoryName(request.categoryName());
        entity.setStatus(request.status());
        this.updateById(entity);
        return this.getDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        long testCount = this.testMapper.selectCount(Wrappers.<TestEntity>lambdaQuery()
                .in(TestEntity::getCategoryId, ids)
                .eq(TestEntity::getDeleted, NORMAL));
        if (testCount > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, HttpStatus.CONFLICT.value(),
                    "分类已关联题型，不能删除");
        }
        this.lambdaUpdate()
                .set(CategoryEntity::getDeleted, DELETED)
                .in(CategoryEntity::getId, ids)
                .eq(CategoryEntity::getDeleted, NORMAL)
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Long> orderedIds) {
        if (new HashSet<>(orderedIds).size() != orderedIds.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.value(), "分类 ID 不能重复");
        }
        Map<Long, CategoryEntity> categoryMap = this.listByIds(orderedIds).stream()
                .collect(Collectors.toMap(CategoryEntity::getId, Function.identity(), (first, second) -> first));
        if (categoryMap.size() != orderedIds.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.value(), "分类不存在");
        }
        List<Integer> sortNumbers = categoryMap.values().stream()
                .map(CategoryEntity::getSortNo)
                .sorted((first, second) -> Integer.compare(second, first))
                .toList();
        List<CategoryEntity> updates = new ArrayList<>(orderedIds.size());
        for (int index = 0; index < orderedIds.size(); index++) {
            CategoryEntity entity = new CategoryEntity();
            entity.setId(orderedIds.get(index));
            entity.setSortNo(sortNumbers.get(index));
            updates.add(entity);
        }
        this.updateBatchById(updates);
    }
}
