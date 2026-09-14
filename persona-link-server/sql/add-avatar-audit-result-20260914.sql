-- 在部署头像审核服务前执行；已装过待审字段的环境也需要此表。
-- 仅保存任务号和首次审核结论，使早到回调和服务重启后的补偿不依赖内存。
CREATE TABLE IF NOT EXISTS t_avatar_audit_result (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    trace_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '微信审核任务号',
    passed TINYINT NOT NULL COMMENT '1审核通过，0未通过',
    deleted TINYINT NOT NULL DEFAULT 0,
    create_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_avatar_audit_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='微信头像审核首次结果';
