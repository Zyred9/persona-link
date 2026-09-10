package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.MiniappUserEntity;
import org.apache.ibatis.annotations.Mapper;

/** 小程序用户资料 Mapper。 */
@Mapper
public interface MiniappUserMapper extends BaseMapper<MiniappUserEntity> {
}
