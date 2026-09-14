-- 已发布协议的最小增量：补充头像实际存储服务方，不覆盖后台维护的其他正文。
-- 执行前核对目标数据库并备份 type=2 的记录。重复执行不再次增加版本号。
-- 若 updated_documents=0 且正文没有阿里云说明，应在后台人工补充并发布。
SET NAMES utf8mb4;

UPDATE t_legal_document
SET content = REPLACE(content,
        '| 腾讯云       | 服务器托管、数据库存储、网络安全   | 用户标识、答题记录、配对记录、运行日志 |',
        CONCAT('| 腾讯云       | 服务器托管、数据库存储、网络安全   | 用户标识、答题记录、配对记录、运行日志 |', CHAR(10),
            '| 阿里云（阿里云计算有限公司） | OSS 对象存储，用于保存和展示你主动上传的头像 | 头像图片及图片访问地址 |')),
    version = version + 1,
    update_date = CURRENT_TIMESTAMP
WHERE type = 2
  AND deleted = 0
  AND LOCATE('| 腾讯云       | 服务器托管、数据库存储、网络安全   | 用户标识、答题记录、配对记录、运行日志 |', content) > 0
  AND LOCATE('| 阿里云（阿里云计算有限公司） |', content) = 0;

SELECT ROW_COUNT() AS updated_documents;
SELECT type, version, content FROM t_legal_document WHERE type = 2 AND deleted = 0;
