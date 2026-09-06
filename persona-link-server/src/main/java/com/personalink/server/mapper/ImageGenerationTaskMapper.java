package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.ImageGenerationTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/** AI 双图任务持久化。 */
@Mapper
public interface ImageGenerationTaskMapper extends BaseMapper<ImageGenerationTaskEntity> {
}

