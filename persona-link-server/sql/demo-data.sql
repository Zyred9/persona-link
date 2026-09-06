-- 手工演示数据脚本，由数据库管理员在 schema.sql 执行成功后运行。

INSERT INTO t_category (category_name, sort_no, status)
VALUES ('日常偏好', 100, 1);
SET @demo_category_id = LAST_INSERT_ID();

INSERT INTO t_test (test_name, test_type, category_id, status)
VALUES ('演示性格测试', 1, @demo_category_id, 1);
SET @demo_test_id = LAST_INSERT_ID();

INSERT INTO t_test_version (
    test_id, version_no, title, cover_url, description, estimated_minutes,
    draw_question_count, version_status, version_note
)
VALUES (
    @demo_test_id, 1, '你是哪种行动派？', 'https://pgcloud.aitici.com/common/20260831/5d31ed6e4916459c9c8c36750b71d0e0.png',
    '用于验证题型、题目、选项、维度和结果规则的最小演示版本。', 1, 1, 1, 'V1 最小演示数据'
);
SET @demo_version_id = LAST_INSERT_ID();

INSERT INTO t_score_dimension (version_id, dimension_code, dimension_name, sort_no)
VALUES (@demo_version_id, 'ACTION_STYLE', '行动方式', 1);
SET @demo_dimension_id = LAST_INSERT_ID();

INSERT INTO t_question (
    version_id, question_type, dimension_id, min_select_count, max_select_count,
    question_no, question_text, required_flag, sort_no
)
VALUES (
    @demo_version_id, 1, @demo_dimension_id, 1, 1,
    1, '面对一个新计划时，你通常会怎么做？', 1, 1
);
SET @demo_question_id = LAST_INSERT_ID();

INSERT INTO t_question_option (
    question_id, option_code, option_text, dimension_id, score_value, sort_no
)
VALUES
    (@demo_question_id, 'A', '先列出步骤，再开始行动', NULL, 10, 1),
    (@demo_question_id, 'B', '边行动边调整方向', NULL, 0, 2);

INSERT INTO t_result_template (
    version_id, dimension_id, result_code, result_name, score_min, score_max,
    basic_result_json, deep_result_json, share_copy_json, sort_no
)
VALUES
    (
        @demo_version_id, @demo_dimension_id, 'ACTION_FLEXIBLE', '灵活行动者', 0, 40,
        JSON_OBJECT('text', '你擅长顺势而为，在行动中快速调整。'),
        JSON_OBJECT('text', '保留灵活性的同时，提前确认关键边界会让行动更稳。'),
        JSON_OBJECT('text', '我是灵活行动者，你是哪一种？'),
        1
    ),
    (
        @demo_version_id, @demo_dimension_id, 'ACTION_BALANCED', '平衡行动者', 40, 70,
        JSON_OBJECT('text', '你会在计划与行动之间保持平衡。'),
        JSON_OBJECT('text', '你既关注方向，也愿意根据反馈及时修正。'),
        JSON_OBJECT('text', '我是平衡行动者，你是哪一种？'),
        2
    ),
    (
        @demo_version_id, @demo_dimension_id, 'ACTION_PLANNED', '计划行动者', 70, 100,
        JSON_OBJECT('text', '你习惯先想清楚，再有节奏地推进。'),
        JSON_OBJECT('text', '清晰的计划是你的优势，给变化留一点空间会更从容。'),
        JSON_OBJECT('text', '我是计划行动者，你是哪一种？'),
        3
    );
