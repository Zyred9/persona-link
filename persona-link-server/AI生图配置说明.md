# 题型双图生成

入口：后台「题型管理」每行的「AI 生图」。填写关键词后生成封面、详情两张图，预览后点击「采用两张图片」写入草稿，发布后小程序才使用新图。

## 启用

1. 在目标数据库执行 `sql/add-image-generation-task.sql`（先备份；脚本仅创建任务表，不改题型数据）。必须在启动新版后端前完成。
2. 配置 `src/main/resources/application.yml` 的 `app.image-generation` 或对应环境变量。真实密钥不要提交到仓库。
3. 确保现有 OSS 上传配置可用。生成成功的临时图片会转存到本项目的 OSS；未采用的图片也受素材引用保护。
4. 重启后端，刷新后台。缺少密钥或 OSS 配置时会拒绝创建任务，不调用付费接口。

## 后端路由

| 环境变量 | 用途 |
| --- | --- |
| `IMAGE_GENERATION_PROVIDER` | `1` 千问，`2` 火山；不在前端暴露选择或密钥 |
| `QWEN_IMAGE_API_KEY` | 阿里百炼 API Key |
| `QWEN_IMAGE_BASE_URL` | 默认 `https://dashscope.aliyuncs.com`；也支持对应区域业务空间的 `https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com` 等官方域名，须与 Key 区域一致 |
| `QWEN_IMAGE_MODEL` | 默认 `qwen-image-3.0`，可配置 `qwen-image-3.0-pro` |
| `VOLC_IMAGE_ACCESS_KEY` | 火山视觉服务 Access Key，不是方舟 Ark Bearer Key |
| `VOLC_IMAGE_SECRET_KEY` | 火山视觉服务 Secret Key |
| `VOLC_IMAGE_BASE_URL` | 默认 `https://visual.volcengineapi.com` |
| `VOLC_IMAGE_MODEL` | 即梦4 `req_key`，默认 `jimeng_t2i_v40`，也支持 `t2i_v40_jimeng`；以账号开通的官方接口版本为准 |
| `VOLC_IMAGE_REGION` | `cn-north-1` |

只需配置正在使用的服务商。新任务保存当时的服务商、模型、地址、区域；切换配置不会更换旧任务的查询路由。还有旧任务待完成时，不要移除旧服务商凭据。

## 图片与提示词

- 运营关键词最多 1500 字，服务端追加题型名称、画风与独立构图要求。
- Web 可分别输入封面、详情的宽高，默认 `800×800` / `1100×500`，提示词、任务快照和最终图片均使用所选尺寸。宽高为至少256的整数，总像素262144至4194304，比例1:3至3:1（两家共同范围）；2048²是总像素上限，不是单边上限，支持 `2200×1000`。
- 最终转存为所选尺寸 PNG；异常比例完整等比留白，不拉伸、不裁剪。保留模型 AI 标识。千问直接提交目标宽高；火山低于1024²像素时整数倍放大后提交，再转存回目标尺寸。
- 已有任务表的环境先执行 `sql/add-image-generation-resolution.sql`，再重启后端。旧任务沿用原尺寸；修改尺寸须创建新任务，重试不会改变已保存尺寸。
- 两张图分别调用模型，提示词约束风格，但不承诺独立生成的角色完全一致。

## 异步与失败处理

- 创建接口只持久化任务并提交到独立有界线程池，两家均使用上游原生提交/任务查询 API。
- 状态：1排队、2生成中、3成功待采用、4失败、5已采用。每个题型同时只有一个活跃任务。
- 关闭网页不停止任务。默认每5秒查询上游，20分钟等待上限；页面每2秒读取本地进度。
- 已完成图片不会在重试中重复生成；查询超时后仍使用原上游任务编号。
- 提交结果未知（断网、5xx、缺任务编号等）时，禁止直接重试付费请求。需先到供应商控制台核实，再明确新建任务。
- 服务重启将等待中的任务标为失败，保留上游编号和已完成图片；点击重试继续查询。上游任务及临时 URL 有时效，建议及时处理。
- 当前恢复机制按单实例部署设计，多实例前需升级为任务租约，不能在多实例上直接启用启动回收。
- 下载只允许配置的可信 HTTPS 图片域，禁重定向、私网地址，限制20MB和2400万像素；若供应商更换 CDN，仅在确认归属后修改 `IMAGE_DOWNLOAD_HOST_SUFFIXES`。

## 接口

所有接口需后台登录；POST 需可写权限。密钥、上游地址和签名不返回前端。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/admin/tests/{testId}/image-tasks` | `{requestId: UUID, promptText: 关键词}` 创建双图任务；重发相同请求必须复用 requestId |
| GET | `/api/admin/tests/{testId}/image-tasks/latest` | 恢复该题型最新任务；没有则返回 null |
| GET | `/api/admin/image-tasks/{id}` | 查询进度、生成结果 |
| POST | `/api/admin/image-tasks/{id}/retry` | 重试失败任务，拒绝未知提交的重复付费 |
| POST | `/api/admin/image-tasks/{id}/apply` | 幂等采用到现有草稿；无草稿时复制已发布/下线版本，不自动发布 |

## 验证边界

开发验证使用本地 HTTP mock、任务/尺寸/素材测试及前端运行脚本，没有执行目标数据库迁移、读取真实密钥或调用付费模型。部署后应分别选择两家，用一条测试题型验证生成、采用、发布、小程序图片显示。

官方协议：[千问图像3.0](https://help.aliyun.com/zh/model-studio/qwen-image-generation-and-editing-api-reference)、[火山即梦4](https://www.volcengine.com/docs/85621/1863351)。
