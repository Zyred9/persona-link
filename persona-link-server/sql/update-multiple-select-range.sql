-- 多选题统一规则：最少选择 2 项、最多全选（等于该题有效选项数）。
-- 仅处理有效选项数 >= 2 的题目，避免违反 chk_question_select_range 约束。
-- 可重复执行：已符合规则的题目不会被改变。

UPDATE t_question q
SET q.min_select_count = 2,
    q.max_select_count = (
        SELECT COUNT(*)
        FROM t_question_option o
        WHERE o.question_id = q.id
          AND o.deleted = 0
    )
WHERE q.question_type = 2
  AND q.deleted = 0
  AND (SELECT COUNT(*) FROM t_question_option o WHERE o.question_id = q.id AND o.deleted = 0) >= 2
  AND (q.min_select_count <> 2
       OR q.max_select_count <> (
           SELECT COUNT(*)
           FROM t_question_option o
           WHERE o.question_id = q.id
             AND o.deleted = 0
       ));
