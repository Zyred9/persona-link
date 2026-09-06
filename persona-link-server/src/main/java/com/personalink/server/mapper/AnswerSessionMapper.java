package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.AnswerSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/** 答题会话数据访问接口。 */
@Mapper
public interface AnswerSessionMapper extends BaseMapper<AnswerSessionEntity> {

    int insertIgnore(AnswerSessionEntity entity);
}
