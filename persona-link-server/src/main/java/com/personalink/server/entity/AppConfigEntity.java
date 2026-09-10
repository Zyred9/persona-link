package com.personalink.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.personalink.server.dto.AppConfigSaveRequest;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * t_app_config；按唯一键保存应用通用配置。
 * <p>只存配置项，业务数据保留各自领域表。
 * @author persona-link
 * @since 2026-09-09
 */
@Getter
@Setter
@TableName("t_app_config")
public class AppConfigEntity extends BaseAssessmentEntity {
    /** 全局唯一配置键。 */
    private String configKey;
    /** 配置值，由具体业务接口校验后保存。 */
    private String configValue;
    /** 值类型，参见 AppConfigValueType。 */
    private Integer valueType;
    /** 配置中文名称。 */
    private String configName;
    /** 配置说明。 */
    private String remark;

    /** 将已校验参数组装为待保存配置。 */
    public static AppConfigEntity build(AppConfigSaveRequest request) {
        AppConfigEntity config = new AppConfigEntity();
        config.setConfigKey(request.configKey());
        config.setConfigValue(request.configValue());
        config.setValueType(request.valueType());
        config.setConfigName(request.configName());
        config.setRemark(request.remark());
        config.setUpdateDate(LocalDateTime.now());
        return config;
    }
}
