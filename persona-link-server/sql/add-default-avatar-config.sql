-- 请先执行 add-app-config.sql，再初始化默认头像配置；不覆盖已有配置。
-- 值可在后台「应用配置」中维护；用户未提供头像时，建号（静默登录）即写入资料表，留空则保持空值由客户端吉祥物占位。
INSERT INTO t_app_config (config_key, config_value, value_type, config_name, remark)
VALUES ('miniapp.default_avatar_url', '', 1, '小程序默认头像', '用户未提供头像时写入用户资料表的默认头像地址，留空则保持空值')
ON DUPLICATE KEY UPDATE config_key = VALUES(config_key);
