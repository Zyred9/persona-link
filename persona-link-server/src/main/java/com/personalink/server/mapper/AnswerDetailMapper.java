package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.AnswerDetailEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 答卷明细数据访问接口。 */
@Mapper
public interface AnswerDetailMapper extends BaseMapper<AnswerDetailEntity> {

    int upsertBatch(@Param("items") List<AnswerDetailEntity> items);
}
