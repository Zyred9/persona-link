-- 微信激励视频广告一期。先备份，停服执行一次，再启动新后端；不可重复执行旧报告授权段。
-- 不修改已有报告内容；旧报告按用户授予永久访问权限，避免上线后重新锁定。
CREATE TABLE t_ad_config (
    id BIGINT NOT NULL PRIMARY KEY,
    enabled TINYINT NOT NULL DEFAULT 0,
    ad_unit_id VARCHAR(71) NOT NULL DEFAULT '',
    failure_policy TINYINT NOT NULL DEFAULT 1 COMMENT '1免费放行，2稍后重试',
    updated_by_name VARCHAR(100) NULL,
    updated_at DATETIME NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO t_ad_config(id) VALUES (1);

CREATE TABLE t_report_access (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    open_id VARCHAR(128) NOT NULL,
    report_type TINYINT NOT NULL COMMENT '1单人报告，2双人报告',
    report_id BIGINT NOT NULL COMMENT '单人报告ID或双人会话ID',
    grant_source TINYINT NOT NULL COMMENT '1关闭，2完成，3失败，4迁移',
    granted_at DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_report_access(open_id, report_type, report_id),
    KEY idx_access_quota(open_id, grant_source, granted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_report_ad_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    open_id VARCHAR(128) NOT NULL,
    report_type TINYINT NOT NULL,
    report_id BIGINT NOT NULL,
    task_id CHAR(36) NOT NULL,
    ad_unit_id VARCHAR(71) NOT NULL,
    expires_at DATETIME NOT NULL,
    consumed_at DATETIME NULL,
    outcome TINYINT NULL COMMENT '1完整观看，2加载失败',
    error_code INT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ad_task(task_id),
    KEY idx_task_report(open_id, report_type, report_id, expires_at),
    KEY idx_task_quota(open_id, create_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO t_report_access(open_id, report_type, report_id, grant_source, granted_at)
SELECT s.open_id, 1, r.id, 4, NOW()
FROM t_report r JOIN t_answer_session s ON s.id = r.answer_session_id
WHERE r.deleted = 0 AND s.deleted = 0;

INSERT INTO t_report_access(open_id, report_type, report_id, grant_source, granted_at)
SELECT p.initiator_open_id, 2, p.id, 4, NOW()
FROM t_pair_session p JOIN t_pair_report r ON r.pair_session_id = p.id AND r.deleted = 0
WHERE p.deleted = 0
UNION
SELECT p.partner_open_id, 2, p.id, 4, NOW()
FROM t_pair_session p JOIN t_pair_report r ON r.pair_session_id = p.id AND r.deleted = 0
WHERE p.deleted = 0 AND p.partner_open_id IS NOT NULL;
