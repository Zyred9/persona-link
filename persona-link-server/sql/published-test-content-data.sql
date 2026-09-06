-- 为首页与分类测试数据补齐可答题内容，每个版本仅生成 5 道测试题，可重复执行。
-- 仅处理 home-ui-data.sql 和 category-test-data.sql 创建的已发布版本。

START TRANSACTION;

SET @test_question_count = 5;

UPDATE t_test_version
SET draw_question_count = @test_question_count
WHERE version_status = 4
  AND deleted = 0
  AND version_note IN ('首页展示数据', '分类测试数据');

INSERT INTO t_score_dimension (version_id, dimension_code, dimension_name, sort_no, deleted)
SELECT tv.id, 'MAIN', '综合倾向', 1, 0
FROM t_test_version tv
WHERE tv.version_status = 4
  AND tv.deleted = 0
  AND tv.version_note IN ('首页展示数据', '分类测试数据')
  AND NOT EXISTS (
      SELECT 1
      FROM t_score_dimension d
      WHERE d.version_id = tv.id AND d.deleted = 0
  );

INSERT INTO t_question (
    version_id, question_type, dimension_id, min_select_count, max_select_count,
    question_no, question_text, required_flag, sort_no, deleted
)
WITH RECURSIVE question_numbers AS (
    SELECT 1 AS question_no
    UNION ALL
    SELECT question_no + 1
    FROM question_numbers
    WHERE question_no < @test_question_count
)
SELECT
    tv.id,
    1,
    (
        SELECT d.id
        FROM t_score_dimension d
        WHERE d.version_id = tv.id AND d.deleted = 0
        ORDER BY d.sort_no, d.id
        LIMIT 1
    ),
    1,
    1,
    numbers.question_no,
    CONCAT('第', numbers.question_no, '题：以下哪种描述更符合你？'),
    1,
    @test_question_count - numbers.question_no + 1,
    0
FROM t_test_version tv
JOIN question_numbers numbers ON numbers.question_no <= @test_question_count
WHERE tv.version_status = 4
  AND tv.deleted = 0
  AND tv.version_note IN ('首页展示数据', '分类测试数据')
  AND NOT EXISTS (
      SELECT 1
      FROM t_question q
      WHERE q.version_id = tv.id
        AND q.question_no = numbers.question_no
        AND q.deleted = 0
  );

INSERT INTO t_question_option (
    question_id, option_code, option_text, dimension_id, score_value, sort_no, deleted
)
SELECT q.id, 'A', '更偏向稳妥和有计划的方式', NULL, 0, 2, 0
FROM t_question q
JOIN t_test_version tv ON tv.id = q.version_id
WHERE q.deleted = 0
  AND q.question_no <= @test_question_count
  AND tv.deleted = 0
  AND tv.version_note IN ('首页展示数据', '分类测试数据')
  AND NOT EXISTS (
      SELECT 1 FROM t_question_option o
      WHERE o.question_id = q.id AND o.option_code = 'A'
  );

INSERT INTO t_question_option (
    question_id, option_code, option_text, dimension_id, score_value, sort_no, deleted
)
SELECT q.id, 'B', '更偏向灵活和随机应变的方式', NULL, 100, 1, 0
FROM t_question q
JOIN t_test_version tv ON tv.id = q.version_id
WHERE q.deleted = 0
  AND q.question_no <= @test_question_count
  AND tv.deleted = 0
  AND tv.version_note IN ('首页展示数据', '分类测试数据')
  AND NOT EXISTS (
      SELECT 1 FROM t_question_option o
      WHERE o.question_id = q.id AND o.option_code = 'B'
  );

INSERT INTO t_result_template (
    version_id, dimension_id, result_code, result_name, score_min, score_max,
    basic_result_json, deep_result_json, share_copy_json, sort_no, deleted
)
SELECT
    d.version_id, d.id, 'STEADY', '稳健规划型', 0, 50,
    JSON_OBJECT('text', '你更偏向稳妥、清晰和有计划地推进事情。'),
    JSON_OBJECT('text', '稳定是你的优势，适当给变化留出空间会更加从容。'),
    JSON_OBJECT('text', '我是稳健规划型，你是哪一种？'),
    1, 0
FROM t_score_dimension d
JOIN t_test_version tv ON tv.id = d.version_id
WHERE d.deleted = 0
  AND tv.deleted = 0
  AND tv.version_note IN ('首页展示数据', '分类测试数据')
  AND NOT EXISTS (
      SELECT 1 FROM t_result_template r
      WHERE r.version_id = d.version_id AND r.result_code = 'STEADY'
  );

INSERT INTO t_result_template (
    version_id, dimension_id, result_code, result_name, score_min, score_max,
    basic_result_json, deep_result_json, share_copy_json, sort_no, deleted
)
SELECT
    d.version_id, d.id, 'FLEXIBLE', '灵活行动型', 50, 100,
    JSON_OBJECT('text', '你更偏向灵活、开放和随机应变地处理事情。'),
    JSON_OBJECT('text', '适应变化是你的优势，提前确认关键边界会更加稳妥。'),
    JSON_OBJECT('text', '我是灵活行动型，你是哪一种？'),
    2, 0
FROM t_score_dimension d
JOIN t_test_version tv ON tv.id = d.version_id
WHERE d.deleted = 0
  AND tv.deleted = 0
  AND tv.version_note IN ('首页展示数据', '分类测试数据')
  AND NOT EXISTS (
      SELECT 1 FROM t_result_template r
      WHERE r.version_id = d.version_id AND r.result_code = 'FLEXIBLE'
  );

COMMIT;
