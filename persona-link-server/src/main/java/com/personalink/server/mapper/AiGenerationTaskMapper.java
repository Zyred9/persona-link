package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.AiGenerationTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/** AI 题库生成任务数据访问接口。 */
@Mapper
public interface AiGenerationTaskMapper extends BaseMapper<AiGenerationTaskEntity> {
}
