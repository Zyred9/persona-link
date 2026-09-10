-- 小程序用户资料增量，可重复执行；历史会话首次读取资料时补建用户。
CREATE TABLE IF NOT EXISTS t_miniapp_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    open_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '微信用户唯一标识',
    nickname VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户主动填写的昵称',
    avatar_url VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '本服务上传的头像地址',
    deleted TINYINT NOT NULL DEFAULT 0,
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_miniapp_user_open_id (open_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小程序用户资料';
