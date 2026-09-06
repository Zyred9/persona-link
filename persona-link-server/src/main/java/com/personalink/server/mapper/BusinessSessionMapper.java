package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.BusinessSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 持久化业务会话 Mapper。
 */
@Mapper
public interface BusinessSessionMapper extends BaseMapper<BusinessSessionEntity> {
}
