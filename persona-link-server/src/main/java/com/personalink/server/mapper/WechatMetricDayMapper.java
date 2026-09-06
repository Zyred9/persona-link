package com.personalink.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalink.server.entity.WechatMetricDayEntity;
import org.apache.ibatis.annotations.Mapper;

/** 微信官方日访问数据 Mapper。 */
@Mapper
public interface WechatMetricDayMapper extends BaseMapper<WechatMetricDayEntity> {
}
