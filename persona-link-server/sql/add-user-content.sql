-- 用户反馈和协议配置增量；不写入占位协议正文。执行前核对目标数据库。
CREATE TABLE IF NOT EXISTS t_feedback (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    open_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '提交用户',
    request_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '用户内幂等请求号',
    content VARCHAR(500) NOT NULL COMMENT '反馈或举报纯文本',
    deleted TINYINT NOT NULL DEFAULT 0,
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_feedback_request (open_id, request_id),
    KEY idx_feedback_created (deleted, create_date, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户反馈';
CREATE TABLE IF NOT EXISTS t_legal_document (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    type TINYINT NOT NULL COMMENT '1用户协议，2隐私保护指引，3免责声明',
    title VARCHAR(100) NOT NULL COMMENT '标题',
    content MEDIUMTEXT NOT NULL COMMENT '协议纯文本正文',
    version BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '发布版本',
    deleted TINYINT NOT NULL DEFAULT 0,
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_legal_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='协议配置';
