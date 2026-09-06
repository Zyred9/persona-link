-- 已执行旧版 schema.sql 的数据库使用本增量脚本；新建库直接执行最新版 schema.sql。
ALTER TABLE t_ai_generation_task
    ADD COLUMN request_id VARCHAR(64) NULL COMMENT '创建请求幂等编号' AFTER task_no,
    ADD COLUMN operator_id BIGINT UNSIGNED NULL COMMENT '创建任务的后台操作人ID' AFTER version_id,
    ADD COLUMN model_name VARCHAR(64) NULL COMMENT '实际使用的AI模型名称' AFTER operator_id,
    ADD COLUMN submitted_at DATETIME NULL COMMENT '提交题型库时间' AFTER completed_at;

-- 旧任务没有这些快照值，使用当前最早的有效管理员补齐后再收紧约束。
UPDATE t_ai_generation_task
SET request_id = CONCAT('LEGACY-', task_no),
    operator_id = (SELECT MIN(id) FROM t_admin_account WHERE deleted = 0),
    model_name = 'deepseek-v4-flash'
WHERE request_id IS NULL OR operator_id IS NULL OR model_name IS NULL;

ALTER TABLE t_ai_generation_task
    MODIFY COLUMN request_id VARCHAR(64) NOT NULL COMMENT '创建请求幂等编号',
    MODIFY COLUMN operator_id BIGINT UNSIGNED NOT NULL COMMENT '创建任务的后台操作人ID',
    MODIFY COLUMN model_name VARCHAR(64) NOT NULL COMMENT '实际使用的AI模型名称',
    ADD UNIQUE KEY uk_ai_task_request (request_id);
