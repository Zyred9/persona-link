-- 请先执行 add-app-config.sql，再初始化联系邮箱配置；不覆盖已有配置。
-- 值可在后台「通用配置」中维护；小程序设置页展示，留空时展示「未配置」。
INSERT INTO t_app_config (config_key, config_value, value_type, config_name, remark)
VALUES ('miniapp.contact_email', 'zyred_11211@163.com', 1, '小程序联系邮箱', '设置页联系邮箱展示值，留空时展示未配置')
ON DUPLICATE KEY UPDATE config_key = VALUES(config_key);
