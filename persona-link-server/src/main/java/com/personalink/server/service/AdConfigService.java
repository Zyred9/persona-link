package com.personalink.server.service;
import com.baomidou.mybatisplus.spring.service.IService;
import com.personalink.server.entity.AdConfigEntity;
import com.personalink.server.entity.AdminAccountEntity;
import com.personalink.server.dto.*;
public interface AdConfigService extends IService<AdConfigEntity> {
    AdConfigEntity current();
    AdConfigEntity lockCurrent();
    AdConfigResponse readConfig();
    AdConfigResponse saveConfig(AdConfigSaveRequest request, AdminAccountEntity operator);
}
