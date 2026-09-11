package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalink.server.dto.HomeConfigResponse;
import com.personalink.server.dto.HomeConfigSaveRequest;
import com.personalink.server.dto.AppConfigQuery;
import com.personalink.server.dto.AppConfigSaveRequest;
import com.personalink.server.dto.MiniappConfigResponse;
import com.personalink.server.dto.PageResponse;
import com.personalink.server.dto.PairConfigResponse;
import com.personalink.server.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.List;
import com.personalink.server.entity.AppConfigEntity;
import com.personalink.server.enums.AppConfigValueType;
import com.personalink.server.mapper.AppConfigMapper;
import com.personalink.server.service.AppConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** 通用配置存储；通过具体业务接口读取，避免向小程序暴露其他配置。 */
@Service
public class AppConfigServiceImpl extends ServiceImpl<AppConfigMapper, AppConfigEntity> implements AppConfigService {
    /** 小程序首页标题图配置键。 */
    private static final String HOME_TITLE_IMAGE_URL = "miniapp.home.title_image_url";
    /** 加入双人测试页头图配置键。 */
    private static final String PAIR_JOIN_HERO_IMAGE_URL = "miniapp.pair.join_hero_image_url";

    @Override
    public HomeConfigResponse readHomeConfig() {
        return new HomeConfigResponse(this.readConfigValue(HOME_TITLE_IMAGE_URL));
    }

    @Override
    public PairConfigResponse readPairConfig() {
        return new PairConfigResponse(this.readConfigValue(PAIR_JOIN_HERO_IMAGE_URL));
    }

    private String readConfigValue(String configKey) {
        AppConfigEntity config = this.getOne(Wrappers.<AppConfigEntity>lambdaQuery()
                .eq(AppConfigEntity::getConfigKey, configKey)
                .eq(AppConfigEntity::getDeleted, 0), false);
        return Objects.isNull(config) || Objects.isNull(config.getConfigValue()) ? "" : config.getConfigValue();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HomeConfigResponse saveHomeConfig(HomeConfigSaveRequest request) {
        AppConfigEntity config = new AppConfigEntity();
        config.setConfigKey(HOME_TITLE_IMAGE_URL);
        config.setConfigValue(request.titleImageUrl());
        config.setValueType(AppConfigValueType.STRING.getCode());
        config.setConfigName("小程序首页标题图");
        config.setRemark("空值使用小程序内置home-title.png");
        this.baseMapper.upsert(config);
        return new HomeConfigResponse(config.getConfigValue());
    }

    @Override
    public PageResponse<AppConfigEntity> pageConfigs(AppConfigQuery query) {
        var wrapper = Wrappers.<AppConfigEntity>lambdaQuery()
                .eq(AppConfigEntity::getDeleted, 0)
                // 首页标题图由「首页配置」专用页面维护，通用配置列表不重复展示。
                .ne(AppConfigEntity::getConfigKey, HOME_TITLE_IMAGE_URL);
        if (Objects.nonNull(query.getKeyword()) && !query.getKeyword().isBlank()) {
            wrapper.and(condition -> condition.like(AppConfigEntity::getConfigKey, query.getKeyword())
                    .or().like(AppConfigEntity::getConfigName, query.getKeyword()));
        }
        long total = this.count(wrapper);
        wrapper.orderByDesc(AppConfigEntity::getCreateDate)
                .orderByDesc(AppConfigEntity::getId)
                .last("LIMIT " + (query.getPage() - 1) * query.getSize() + ", " + query.getSize());
        return new PageResponse<>(this.list(wrapper), total, query.getPage(), query.getSize());
    }

    @Override
    public AppConfigEntity getDetail(Long id) {
        AppConfigEntity config = this.getOne(Wrappers.<AppConfigEntity>lambdaQuery()
                .eq(AppConfigEntity::getId, id)
                .eq(AppConfigEntity::getDeleted, 0), false);
        if (Objects.isNull(config)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 404, "配置不存在或已删除");
        }
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppConfigEntity create(AppConfigSaveRequest request) {
        AppConfigEntity config = AppConfigEntity.build(request);
        // 唯一键保留在软删除行上；条件恢复只允许一名并发写入者成功。
        if (this.baseMapper.restoreDeleted(config) == 0) {
            try {
                this.save(config);
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(400, "配置键已存在，请刷新列表后编辑");
            }
        }
        return this.getOne(Wrappers.<AppConfigEntity>lambdaQuery()
                .eq(AppConfigEntity::getConfigKey, config.getConfigKey())
                .eq(AppConfigEntity::getDeleted, 0), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppConfigEntity update(Long id, AppConfigSaveRequest request) {
        AppConfigEntity current = this.getDetail(id);
        if (!current.getConfigKey().equals(request.configKey())) {
            throw new BusinessException(400, "配置键不可修改，请新增配置项");
        }
        AppConfigEntity config = AppConfigEntity.build(request);
        config.setId(id);
        if (!this.updateById(config)) {
            throw new BusinessException(400, "配置已发生变化，请刷新后重试");
        }
        return this.getDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        this.lambdaUpdate()
                .set(AppConfigEntity::getDeleted, 1)
                .set(AppConfigEntity::getUpdateDate, LocalDateTime.now())
                .in(AppConfigEntity::getId, ids)
                .eq(AppConfigEntity::getDeleted, 0)
                .update();
    }

    @Override
    public MiniappConfigResponse readMiniappConfig() {
        AppConfigEntity config = this.getOne(Wrappers.<AppConfigEntity>lambdaQuery()
                .eq(AppConfigEntity::getConfigKey, "miniapp.version")
                .eq(AppConfigEntity::getValueType, AppConfigValueType.STRING.getCode())
                .eq(AppConfigEntity::getDeleted, 0), false);
        return new MiniappConfigResponse(Objects.isNull(config) || Objects.isNull(config.getConfigValue())
                ? "" : config.getConfigValue());
    }

}
