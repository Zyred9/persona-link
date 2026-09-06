-- 首页测试图片统一地址。
-- https://pgcloud.aitici.com/common/20260831/5d31ed6e4916459c9c8c36750b71d0e0.png

INSERT INTO t_category (category_name, sort_no, status, deleted)
VALUES ('职场协作', 100, 1, 0);
SET @work_category_id = LAST_INSERT_ID();

INSERT INTO t_category (category_name, sort_no, status, deleted)
VALUES ('相处方式', 90, 1, 0);
SET @relation_category_id = LAST_INSERT_ID();

INSERT INTO t_category (category_name, sort_no, status, deleted)
VALUES ('日常偏好', 80, 1, 0);
SET @daily_category_id = LAST_INSERT_ID();

INSERT INTO t_test (test_name, test_type, category_id, status, home_display, home_sort, deleted)
VALUES ('双人关系角色测试', 2, @relation_category_id, 1, 1, 100, 0);
SET @pair_test_id = LAST_INSERT_ID();

INSERT INTO t_test (test_name, test_type, category_id, status, home_display, home_sort, deleted)
VALUES ('你是哪种牛马性格测试？', 1, @work_category_id, 1, 2, 100, 0);
SET @work_test_id = LAST_INSERT_ID();

INSERT INTO t_test (test_name, test_type, category_id, status, home_display, home_sort, deleted)
VALUES ('你的 X 偏好？', 1, @daily_category_id, 1, 2, 90, 0);
SET @preference_test_id = LAST_INSERT_ID();

INSERT INTO t_test_version (
    test_id, version_no, title, cover_url, description, estimated_minutes,
    draw_question_count, version_status, version_note, published_at, deleted
)
VALUES
    (@pair_test_id, 1,
     '你和 TA，是哪种相处搭子？', 'https://pgcloud.aitici.com/common/20260831/070b0e982dd7468e9cf5adb463f1d841.png', '看看你们更像哪一种相处搭子。',
     3, 20, 4, '首页展示数据', CURRENT_TIMESTAMP, 0),
    (@work_test_id, 1,
     '你是哪种牛马性格测试？', 'https://pgcloud.aitici.com/common/20260831/c453c3ae29694cecbd84f057beade25a.png', '看看你的职场协作和行动风格。',
     3, 22, 4, '首页展示数据', CURRENT_TIMESTAMP, 0),
    (@preference_test_id, 1,
     '你的 X 偏好？', 'https://pgcloud.aitici.com/common/20260831/fc149c78e6a941549742cebd8209a960.png', '看看自己更自然的日常偏好。',
     3, 20, 4, '首页展示数据', CURRENT_TIMESTAMP, 0);
