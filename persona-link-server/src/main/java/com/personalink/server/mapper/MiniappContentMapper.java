package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.dto.MiniappHomeCategoryResponse;
import com.personalink.server.dto.MiniappHomeTestResponse;
import com.personalink.server.dto.MiniappTestDetailResponse;
import com.personalink.server.entity.TestEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 小程序已发布内容数据访问接口。
 */
@Mapper
public interface MiniappContentMapper extends BaseMapper<TestEntity> {

    List<MiniappHomeCategoryResponse> selectHomeCategories();

    List<MiniappHomeTestResponse> selectHomeTests(@Param("homeDisplay") int homeDisplay);

    MiniappTestDetailResponse selectPublishedTestDetail(@Param("testId") Long testId);
}
