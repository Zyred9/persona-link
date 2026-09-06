package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.dto.CategoryQuery;
import com.personalink.server.dto.CategoryResponse;
import com.personalink.server.entity.CategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分类数据访问接口。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<CategoryEntity> {

    List<CategoryResponse> selectCategoryPage(@Param("query") CategoryQuery query,
                                               @Param("offset") long offset,
                                               @Param("size") long size);

    CategoryResponse selectCategoryById(@Param("id") Long id);
}
