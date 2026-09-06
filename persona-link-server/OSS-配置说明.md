# 图片上传配置

后台题型封面、详情图、AI 题库和素材库共用 `POST /api/admin/assets/images`，以 `multipart/form-data` 的 `file` 字段上传，沿用后台登录鉴权。返回 `data.url` 为 OSS 图片的完整访问地址。

启动服务前在运行环境中配置以下变量（IDE 的运行配置也可设置）：

| 环境变量 | 含义 | 示例 |
| --- | --- | --- |
| `OSS_ENDPOINT` | Bucket 所属地域的 OSS Endpoint | `https://oss-cn-shanghai.aliyuncs.com` |
| `OSS_ACCESS_KEY_ID` | 具备该目录访问权限的 RAM AccessKey ID | 使用实际凭据，不提交到仓库 |
| `OSS_ACCESS_KEY_SECRET` | 对应 AccessKey Secret | 使用实际凭据，不提交到仓库 |
| `OSS_BUCKET_NAME` | Bucket 名称 | 使用实际 Bucket |
| `OSS_DOMAIN` | 已绑定该 Bucket 的 HTTPS 图片访问域名，不带对象路径 | `https://images.example.com` |

新图片路径为 `common/yyyyMMdd/UUID.扩展名`，日期使用北京时间的上传当天日期，UUID 不带连字符。PNG、JPG、WebP 保留各自格式，不进行图片转码。例如：`https://images.example.com/common/20260906/00f04c5985d34174b225ee870527b85b.jpg`。

服务端需要 `common/` 的上传、元数据查询、删除和列表权限，同时保留旧 `persona-link/images/` 目录的查询、删除和列表权限。新图片附带应用归属元数据，素材库只展示和删除本应用上传的对象，不接管共享 `common/` 下其他应用的素材。旧 OSS 图片保留原地址，不自动迁移。

素材列表使用连续分页标记，避免只展示第一页。当前会遍历 `common/` 并读取符合命名规则对象的元数据确认归属；共享目录较大时需改用持久素材索引以减少请求。接口行为参考[阿里云 ListObjectsV2 文档](https://www.alibabacloud.com/help/zh/oss/developer-reference/listobjectsv2)。

新素材的 `fileName` 管理标识为 `yyyyMMdd-UUID.扩展名`，删除接口据此定位完整 OSS 路径；图片访问始终使用 `url` 字段。

图片访问域名须允许后台浏览器和小程序读取这些图片；返回的是长期 URL，不包含临时签名。私有 Bucket 需要通过已配置访问能力的 CDN 等提供读取，不能直接将私有对象地址当作公开图片地址。小程序上线还需将实际图片域名加入相应合法域名配置。

支持 PNG、JPG、WebP，每张不超过 5 MB，并校验文件头与声明类型。OSS 未配置或上传失败时返回错误，不会回退到本地保存。素材库继续展示原 `PERSONA_LINK_UPLOAD_DIR`（默认 `uploads`）下的历史图片，旧 `/uploads/` 地址保留；不自动迁移历史文件。已被未删除版本的封面或详情图引用的素材禁止删除。

验证时先上传一张图片，确认返回 URL 能打开，再确认素材库展示、题型保存后预览以及未引用素材删除。仓库自动测试使用模拟 OSS，不会上传或删除真实 Bucket 对象。
