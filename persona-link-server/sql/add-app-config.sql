-- 通用应用配置增量；执行前核对目标数据库。
CREATE TABLE IF NOT EXISTS t_app_config (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '全局唯一配置键',
    config_value LONGTEXT NOT NULL COMMENT '配置值',
    value_type TINYINT NOT NULL COMMENT '1字符串，2数字，3布尔，4JSON',
    config_name VARCHAR(100) NOT NULL COMMENT '配置中文名称',
    remark VARCHAR(500) DEFAULT NULL COMMENT '配置说明',
    deleted TINYINT NOT NULL DEFAULT 0,
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_config_key (config_key),
    CONSTRAINT chk_app_config_value_type CHECK (value_type IN (1, 2, 3, 4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用通用配置';

INSERT INTO t_app_config (config_key, config_value, value_type, config_name, remark)
VALUES ('miniapp.home.title_image_url', '', 1, '小程序首页标题图', '空值使用小程序内置home-title.png')
ON DUPLICATE KEY UPDATE config_key = VALUES(config_key);
