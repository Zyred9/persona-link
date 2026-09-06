package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.AnswerSessionQuestionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 答卷题目快照数据访问接口。 */
@Mapper
public interface AnswerSessionQuestionMapper extends BaseMapper<AnswerSessionQuestionEntity> {

    int insertBatch(@Param("items") List<AnswerSessionQuestionEntity> items);

    int copyFromSession(@Param("sourceSessionId") Long sourceSessionId,
                        @Param("targetSessionId") Long targetSessionId);
}
