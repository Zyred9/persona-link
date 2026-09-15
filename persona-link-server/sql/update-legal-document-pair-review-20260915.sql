-- 双人作答回顾支持互相查看作答后，隐私保护指引（type=2）的双人配对条款需同步披露该功能。
-- 执行前核对目标数据库并备份 type=2 的记录。重复执行不再次增加版本号。
-- 若 updated_documents=0 且正文仍是旧表述，应在后台协议管理人工修改并发布。
SET NAMES utf8mb4;

UPDATE t_legal_document
SET content = REPLACE(content,
        '配对结果仅向成功配对的双方展示。除非页面另有明确说明，我们不会向对方展示你的每一道题的具体答案，也不会主动向其他无关用户公开配对结果。',
        '配对结果仅向成功配对的双方展示。双方完成答题后，双方均可在「作答回顾」中查看彼此的逐题选择；除此之外，我们不会向其他无关用户公开你的配对结果或逐题答案。'),
    version = version + 1,
    update_date = CURRENT_TIMESTAMP
WHERE type = 2
  AND deleted = 0
  AND LOCATE('配对结果仅向成功配对的双方展示。除非页面另有明确说明，我们不会向对方展示你的每一道题的具体答案，也不会主动向其他无关用户公开配对结果。', content) > 0
  AND LOCATE('双方均可在「作答回顾」中查看彼此的逐题选择', content) = 0;

SELECT ROW_COUNT() AS updated_documents;
SELECT type, version, content FROM t_legal_document WHERE type = 2 AND deleted = 0;
