-- 已有 t_image_generation_task 的环境执行一次；全新环境使用最新建表脚本即可。
-- NOT NULL DEFAULT 同时为历史任务补齐原来的固定尺寸，重试不改变原任务尺寸。
ALTER TABLE t_image_generation_task
    ADD COLUMN cover_width INT NOT NULL DEFAULT 800 COMMENT '封面最终宽度像素' AFTER detail_prompt,
    ADD COLUMN cover_height INT NOT NULL DEFAULT 800 COMMENT '封面最终高度像素' AFTER cover_width,
    ADD COLUMN detail_width INT NOT NULL DEFAULT 1100 COMMENT '详情最终宽度像素' AFTER cover_height,
    ADD COLUMN detail_height INT NOT NULL DEFAULT 500 COMMENT '详情最终高度像素' AFTER detail_width;
