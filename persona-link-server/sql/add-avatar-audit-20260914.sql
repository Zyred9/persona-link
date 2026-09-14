-- 头像接入微信图片内容安全异步审核：审核通过前的头像先存待审字段，不对外生效。
-- 回调按 pending_avatar_trace_id 关联用户，审核结果到达后转正或丢弃。

ALTER TABLE t_miniapp_user
    ADD COLUMN pending_avatar_url VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '待审核头像地址，审核通过后替换 avatar_url' AFTER avatar_url,
    ADD COLUMN pending_avatar_trace_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '微信图片审核任务 trace_id' AFTER pending_avatar_url,
    ADD INDEX idx_miniapp_user_avatar_trace (pending_avatar_trace_id);
