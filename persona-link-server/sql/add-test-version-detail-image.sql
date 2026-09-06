-- 在目标数据库执行；仅新增可空字段，不修改任何已有图片或版本数据，可重复执行。
-- 全新数据库已由 schema.sql 包含该字段。
SET @detail_image_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 't_test_version'
              AND column_name = 'detail_image_url'),
    'SELECT ''detail_image_url already exists'' AS migration_result',
    'ALTER TABLE t_test_version ADD COLUMN detail_image_url VARCHAR(500) NULL COMMENT ''版本详情整图快照，与首页封面独立'' AFTER cover_url'
);
PREPARE detail_image_statement FROM @detail_image_ddl;
EXECUTE detail_image_statement;
DEALLOCATE PREPARE detail_image_statement;
