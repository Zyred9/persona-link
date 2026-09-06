-- 仅适用于仍有旧首页配置表、且 t_test 尚无 home_display/home_sort 字段的数据库，只执行一次。
-- 全新数据库直接执行 schema.sql；本脚本会迁移当前已发布配置，并将旧表改名保留用于回滚。
ALTER TABLE t_test
    ADD COLUMN home_display TINYINT NOT NULL DEFAULT 0 COMMENT '首页位置：0普通列表，1焦点位，2推荐位' AFTER status,
    ADD COLUMN home_sort INT NOT NULL DEFAULT 0 COMMENT '首页排序值，越大越靠前' AFTER home_display,
    ADD KEY idx_test_home (home_display, status, deleted, home_sort);

UPDATE t_test t
LEFT JOIN (
    SELECT s.test_id, MIN(s.slot_type) AS home_display, MAX(s.sort_no) AS home_sort
    FROM t_home_config_version h
    JOIN t_home_slot s ON s.config_version_id = h.id AND s.deleted = 0
    WHERE h.id = (
        SELECT current_home.id
        FROM t_home_config_version current_home
        WHERE current_home.config_status = 2 AND current_home.deleted = 0
        ORDER BY current_home.published_at DESC, current_home.id DESC
        LIMIT 1
    )
    GROUP BY s.test_id
) home ON home.test_id = t.id
SET t.home_display = COALESCE(home.home_display, 0),
    t.home_sort = COALESCE(home.home_sort, 0)
WHERE t.deleted = 0;

RENAME TABLE
    t_home_category TO t_home_category_legacy_20260904,
    t_home_slot TO t_home_slot_legacy_20260904,
    t_home_config_version TO t_home_config_version_legacy_20260904;
