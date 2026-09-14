-- 反馈支持匿名提交：不建号的游客也能反馈和举报，openid 与昵称允许为空。
-- 匿名写入空串而不是 NULL，保留 (open_id, request_id) 唯一键的重试幂等能力。
-- 已执行过不要重复执行；重复执行前先核对目标库列定义。

ALTER TABLE t_feedback
    MODIFY COLUMN open_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '提交用户；匿名提交为空',
    ADD COLUMN nickname VARCHAR(32) NOT NULL DEFAULT '' COMMENT '提交时的昵称快照；匿名或未设置时为空' AFTER open_id;
