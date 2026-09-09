-- 仅供审核，尚未执行。2026-09-09 本机 persona_link：题目 1 的版本 1 不存在，
-- 选项 1、2 无答案引用，题目无答卷快照/答案引用。不要将此脚本用于其他数据库。
-- 默认最后 ROLLBACK；用户批准且核对查询结果后，才把最后一行换成 COMMIT。
-- 只软删除这三个精确 ID，不做全库孤立数据清理，不删除任何用户数据。
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
START TRANSACTION;

SELECT DATABASE() AS target_database;
SELECT id FROM t_test_version WHERE id = 1 FOR UPDATE;
SELECT id, version_id, deleted FROM t_question WHERE id = 1 FOR UPDATE;
SELECT id, question_id, deleted FROM t_question_option WHERE question_id = 1 FOR UPDATE;
SELECT id FROM t_answer_session_question WHERE question_id = 1 FOR UPDATE;
SELECT id FROM t_answer_detail WHERE question_id = 1 OR option_id IN (1, 2) FOR UPDATE;

UPDATE t_question q
SET q.deleted = 1
WHERE DATABASE() = 'persona_link'
  AND q.id = 1 AND q.version_id = 1 AND q.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM t_test_version v WHERE v.id = q.version_id)
  AND NOT EXISTS (SELECT 1 FROM t_answer_session_question s WHERE s.question_id = q.id)
  AND NOT EXISTS (SELECT 1 FROM t_answer_detail a WHERE a.question_id = q.id OR a.option_id IN (1, 2))
  AND NOT EXISTS (SELECT 1 FROM t_question_option o WHERE o.question_id = q.id AND o.id NOT IN (1, 2));
SET @orphan_question_changed = ROW_COUNT();

UPDATE t_question_option
SET deleted = 1
WHERE @orphan_question_changed = 1
  AND id IN (1, 2) AND question_id = 1 AND deleted = 0;
SELECT @orphan_question_changed AS question_rows, ROW_COUNT() AS option_rows;

ROLLBACK;
