package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.ScoreDimensionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 计分维度数据访问接口。 */
@Mapper
public interface ScoreDimensionMapper extends BaseMapper<ScoreDimensionEntity> {
    int insertBatch(@Param("items") List<ScoreDimensionEntity> items);
    int updateBatch(@Param("items") List<ScoreDimensionEntity> items);
}
