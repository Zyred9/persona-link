package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.PairSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/** 双人配对数据访问接口。 */
@Mapper
public interface PairSessionMapper extends BaseMapper<PairSessionEntity> {

    int insertIgnore(PairSessionEntity entity);
}
