package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.AdminAccountEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 后台账号 Mapper。
 */
@Mapper
public interface AdminAccountMapper extends BaseMapper<AdminAccountEntity> {
}
