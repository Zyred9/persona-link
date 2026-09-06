-- 分类数据
INSERT INTO t_category (category_name, sort_no, status, deleted)
VALUES ('职场协作', 100, 1, 0);
SET @work_category_id = LAST_INSERT_ID();

INSERT INTO t_category (category_name, sort_no, status, deleted)
VALUES ('相处方式', 90, 1, 0);
SET @relation_category_id = LAST_INSERT_ID();

INSERT INTO t_category (category_name, sort_no, status, deleted)
VALUES ('日常偏好', 80, 1, 0);
SET @daily_category_id = LAST_INSERT_ID();

-- 分类下的测试数据
INSERT INTO t_test (test_name, test_type, category_id, status, deleted)
VALUES ('职场沟通风格测试', 1, @work_category_id, 1, 0);
SET @work_communication_test_id = LAST_INSERT_ID();

INSERT INTO t_test (test_name, test_type, category_id, status, deleted)
VALUES ('工作执行风格测试', 1, @work_category_id, 1, 0);
SET @work_execution_test_id = LAST_INSERT_ID();

INSERT INTO t_test (test_name, test_type, category_id, status, deleted)
VALUES ('关系边界感测试', 1, @relation_category_id, 1, 0);
SET @relation_boundary_test_id = LAST_INSERT_ID();

INSERT INTO t_test (test_name, test_type, category_id, status, deleted)
VALUES ('冲突处理方式测试', 1, @relation_category_id, 1, 0);
SET @relation_conflict_test_id = LAST_INSERT_ID();

INSERT INTO t_test (test_name, test_type, category_id, status, deleted)
VALUES ('社交能量偏好测试', 1, @daily_category_id, 1, 0);
SET @daily_social_test_id = LAST_INSERT_ID();

INSERT INTO t_test (test_name, test_type, category_id, status, deleted)
VALUES ('日常决策偏好测试', 1, @daily_category_id, 1, 0);
SET @daily_decision_test_id = LAST_INSERT_ID();

-- 已发布版本；首页只会查询已发布题型
INSERT INTO t_test_version (
    test_id, version_no, title, cover_url, description, estimated_minutes,
    draw_question_count, version_status, version_note, published_at, deleted
)
VALUES
    (@work_communication_test_id, 1,
     '你的职场沟通风格是什么？', 'https://pgcloud.aitici.com/common/20260831/5d31ed6e4916459c9c8c36750b71d0e0.png',
     '看看你在职场沟通中的自然表达方式。', 3, 10, 4, '分类测试数据', CURRENT_TIMESTAMP, 0),
    (@work_execution_test_id, 1,
     '你的工作执行风格是什么？', 'https://pgcloud.aitici.com/common/20260831/5d31ed6e4916459c9c8c36750b71d0e0.png',
     '看看你更习惯怎样推进工作。', 3, 10, 4, '分类测试数据', CURRENT_TIMESTAMP, 0),
    (@relation_boundary_test_id, 1,
     '你的关系边界感如何？', 'https://pgcloud.aitici.com/common/20260831/5d31ed6e4916459c9c8c36750b71d0e0.png',
     '看看你在人际关系中的边界偏好。', 3, 10, 4, '分类测试数据', CURRENT_TIMESTAMP, 0),
    (@relation_conflict_test_id, 1,
     '你习惯怎样处理冲突？', 'https://pgcloud.aitici.com/common/20260831/5d31ed6e4916459c9c8c36750b71d0e0.png',
     '看看你面对分歧时更自然的处理方式。', 3, 10, 4, '分类测试数据', CURRENT_TIMESTAMP, 0),
    (@daily_social_test_id, 1,
     '你的社交能量偏好是什么？', 'https://pgcloud.aitici.com/common/20260831/5d31ed6e4916459c9c8c36750b71d0e0.png',
     '看看什么样的社交节奏更适合你。', 3, 10, 4, '分类测试数据', CURRENT_TIMESTAMP, 0),
    (@daily_decision_test_id, 1,
     '你的日常决策风格是什么？', 'https://pgcloud.aitici.com/common/20260831/5d31ed6e4916459c9c8c36750b71d0e0.png',
     '看看你在日常选择中的自然倾向。', 3, 10, 4, '分类测试数据', CURRENT_TIMESTAMP, 0);
