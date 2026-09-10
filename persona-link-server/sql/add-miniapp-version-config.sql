-- 先执行 add-app-config.sql；幂等初始化版本号，不覆盖现有配置或删除状态。
INSERT INTO t_app_config (config_key, config_value, value_type, config_name, remark)
VALUES ('miniapp.version', '1.0.0', 1, '小程序版本号', '设置页版本信息展示值')
ON DUPLICATE KEY UPDATE config_key = VALUES(config_key);
