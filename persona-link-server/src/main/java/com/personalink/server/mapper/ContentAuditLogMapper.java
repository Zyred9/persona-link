package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.ContentAuditLogEntity;
import org.apache.ibatis.annotations.Mapper;

/** 内容审计数据访问接口。 */
@Mapper
public interface ContentAuditLogMapper extends BaseMapper<ContentAuditLogEntity> {
}
