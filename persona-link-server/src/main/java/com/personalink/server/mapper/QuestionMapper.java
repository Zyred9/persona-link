package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.QuestionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 题目数据访问接口。 */
@Mapper
public interface QuestionMapper extends BaseMapper<QuestionEntity> {
    int insertBatch(@Param("items") List<QuestionEntity> items);
}
