-- 手工迁移：先部署已移除 iconUrl 的后端和后台，再在明确选定的目标数据库执行。
-- 会永久删除 t_test.icon_url 中的旧图标地址；执行前请备份 t_test。
-- 不删除任何图片文件，不改封面、详情图或测试版本；可重复执行。
SET @test_icon_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 't_test'
              AND column_name = 'icon_url'),
    'ALTER TABLE t_test DROP COLUMN icon_url',
    'SELECT ''icon_url does not exist'' AS migration_result'
);
PREPARE test_icon_statement FROM @test_icon_ddl;
EXECUTE test_icon_statement;
DEALLOCATE PREPARE test_icon_statement;
