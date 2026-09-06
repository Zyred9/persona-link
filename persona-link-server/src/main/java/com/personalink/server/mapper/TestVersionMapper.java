package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.TestVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 题型版本数据访问接口。 */
@Mapper
public interface TestVersionMapper extends BaseMapper<TestVersionEntity> {
    int cloneDimensions(@Param("sourceVersionId") Long sourceVersionId,
                        @Param("targetVersionId") Long targetVersionId);
    int cloneQuestions(@Param("sourceVersionId") Long sourceVersionId,
                       @Param("targetVersionId") Long targetVersionId);
    int cloneOptions(@Param("sourceVersionId") Long sourceVersionId,
                     @Param("targetVersionId") Long targetVersionId);
    int cloneResults(@Param("sourceVersionId") Long sourceVersionId,
                     @Param("targetVersionId") Long targetVersionId);
}
