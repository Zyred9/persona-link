package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.AvatarAuditResultEntity;
import org.apache.ibatis.annotations.Mapper;

/** 微信头像审核结果持久化。 */
@Mapper
public interface AvatarAuditResultMapper extends BaseMapper<AvatarAuditResultEntity> {
}
