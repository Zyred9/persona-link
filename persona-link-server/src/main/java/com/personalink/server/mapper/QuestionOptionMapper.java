package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.QuestionOptionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 题目选项数据访问接口。 */
@Mapper
public interface QuestionOptionMapper extends BaseMapper<QuestionOptionEntity> {
    int insertBatch(@Param("items") List<QuestionOptionEntity> items);
}
