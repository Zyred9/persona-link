-- 先执行 add-app-config.sql；幂等初始化加入双人测试页头图，不覆盖现有配置或删除状态。
INSERT INTO t_app_config (config_key, config_value, value_type, config_name, remark)
VALUES ('miniapp.pair.join_hero_image_url', '', 1, '加入双人测试页头图', '空值或图片加载失败时隐藏加入页头图')
ON DUPLICATE KEY UPDATE config_key = VALUES(config_key);
