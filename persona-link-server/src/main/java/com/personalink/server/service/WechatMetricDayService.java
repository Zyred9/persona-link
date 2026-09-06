package com.personalink.server.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.dto.WechatMetricDayResponse;
import com.personalink.server.entity.WechatMetricDayEntity;

import java.time.LocalDate;
import java.util.List;

/** 微信官方日访问数据服务。 */
public interface WechatMetricDayService extends IService<WechatMetricDayEntity> {
    WechatMetricDayResponse sync(LocalDate statDate);
    List<WechatMetricDayResponse> listSince(LocalDate since);
}
