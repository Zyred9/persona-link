package com.personalink.server.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.dto.HomeConfigResponse;
import com.personalink.server.dto.HomeConfigSaveRequest;
import com.personalink.server.dto.AppConfigQuery;
import com.personalink.server.dto.AppConfigSaveRequest;
import com.personalink.server.dto.MiniappConfigResponse;
import com.personalink.server.dto.PageResponse;
import com.personalink.server.dto.PairConfigResponse;
import java.util.List;
import com.personalink.server.entity.AppConfigEntity;

public interface AppConfigService extends IService<AppConfigEntity> {
    HomeConfigResponse readHomeConfig();
    HomeConfigResponse saveHomeConfig(HomeConfigSaveRequest request);
    PairConfigResponse readPairConfig();
    PageResponse<AppConfigEntity> pageConfigs(AppConfigQuery query);
    AppConfigEntity getDetail(Long id);
    AppConfigEntity create(AppConfigSaveRequest request);
    AppConfigEntity update(Long id, AppConfigSaveRequest request);
    void deleteByIds(List<Long> ids);
    MiniappConfigResponse readMiniappConfig();
}
