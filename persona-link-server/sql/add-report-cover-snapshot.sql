-- 测试记录冗余存储题型封面快照，避免题型版本删除后封面丢失。
-- 单人报告 t_report、双人配对会话 t_pair_session 各加一个 cover_url 快照字段。

ALTER TABLE t_report
    ADD COLUMN cover_url VARCHAR(500) NULL COMMENT '报告生成时的题型封面快照' AFTER result_code;

ALTER TABLE t_pair_session
    ADD COLUMN cover_url VARCHAR(500) NULL COMMENT '配对创建时的题型封面快照' AFTER version_id;

-- 回填存量数据：题型版本仍存在时，用关联版本封面补齐历史记录快照。
UPDATE t_report r
JOIN t_answer_session s ON s.id = r.answer_session_id AND s.deleted = 0
JOIN t_test_version v ON v.id = s.version_id AND v.deleted = 0
SET r.cover_url = v.cover_url
WHERE r.cover_url IS NULL
  AND r.deleted = 0;

UPDATE t_pair_session p
JOIN t_test_version v ON v.id = p.version_id AND v.deleted = 0
SET p.cover_url = v.cover_url
WHERE p.cover_url IS NULL
  AND p.deleted = 0;
