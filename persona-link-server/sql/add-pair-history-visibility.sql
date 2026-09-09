-- 旧库升级脚本；新库 schema.sql 已包含这两个字段。执行前核对目标数据库并备份。
-- 已存在的可见性值保持不变；新增列时已有参与者默认可见。可重复执行。
SET @pair_visibility_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 't_pair_session'
              AND column_name = 'initiator_visible_flag'),
    'SELECT ''initiator_visible_flag already exists'' AS migration_result',
    'ALTER TABLE t_pair_session ADD COLUMN initiator_visible_flag TINYINT NOT NULL DEFAULT 1 COMMENT ''发起者记录可见：1是，0否'' AFTER pair_status'
);
PREPARE pair_visibility_statement FROM @pair_visibility_ddl;
EXECUTE pair_visibility_statement;
DEALLOCATE PREPARE pair_visibility_statement;

SET @pair_visibility_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 't_pair_session'
              AND column_name = 'partner_visible_flag'),
    'SELECT ''partner_visible_flag already exists'' AS migration_result',
    'ALTER TABLE t_pair_session ADD COLUMN partner_visible_flag TINYINT NOT NULL DEFAULT 1 COMMENT ''受邀者记录可见：1是，0否'' AFTER initiator_visible_flag'
);
PREPARE pair_visibility_statement FROM @pair_visibility_ddl;
EXECUTE pair_visibility_statement;
DEALLOCATE PREPARE pair_visibility_statement;
