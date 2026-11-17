# AI 问答 API

AI 接口沿用 `code/message/data`；成功 code 为 `OK`。列表返回 `items/total/page/size`，页码从1开始，每页1–100条。时间使用服务端业务时间，日期统计按 Asia/Shanghai 自然日。

## 认证及权限

| 接口前缀 | 身份与权限 |
| --- | --- |
| `/api/v1/public/ai` | 访客。除创建 guest-session 外，须使用 `X-AI-Guest-Token`。不接受 buyerId，不查询私人业务，不提供跨设备历史列表 |
| `/api/v1/buyer/ai` | JWT BUYER；只访问本人会话、订单及售后 |
| `/api/v1/admin/ai/knowledge` | OWNER 或当前数据库授予 AI 模块的 STAFF |
| `/api/v1/admin/ai/rules` | OWNER 或当前数据库授予 AI 模块的 STAFF |
| `/api/v1/admin/ai/conversations` | OWNER 或当前数据库授予 AI 模块的 STAFF；正文脱敏，读取详情留审计 |
| `/api/v1/admin/ai/statistics` | OWNER 或当前数据库授予 AI 模块的 STAFF |
| `/api/v1/admin/ai/models` | OWNER，包含删除、profiles、测试、激活和关闭子路径；AI 权限不能授予店员模型设置权限 |

V9 在现有权限约束中增加 AI，已有店员默认没有此权限。后台每次请求读取当前授权，旧 JWT 不会绕过撤权。未知后台端点继续拒绝未分类店员访问。管理员在商城咨询使用访客身份。

## 错误

| HTTP | code | 语义 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | 无效字段、上下文或配置 |
| 401/403 | UNAUTHORIZED/FORBIDDEN | 身份无效或权限不足 |
| 404 | NOT_FOUND | 内容不存在、已删除、过期或不属于当前身份；他人订单与不存在订单一致 |
| 409 | AI_CONFLICT | 版本竞争、幂等负载变化、会话忙、重复订阅或模型配置仍在使用 |
| 429 | AI_RATE_LIMIT | 接受前频率限制；下一分钟重试 |
| 503 | AI_ENCRYPTION_KEY_REQUIRED/AI_CREDENTIAL_ERROR | 主密钥缺失或密文不能解密 |
| 503 | AI_DISABLED/AI_CHAT_UNAVAILABLE/AI_EMBEDDING_UNAVAILABLE | 相应在线能力不可用；业务问答可走基础服务 |

第三方错误不直接返回供应商正文。配置、知识和规则更改只返回安全投影；数据库内部行不是 API 响应契约。

## 商品咨询属性

商品保存接口支持以下可选平级字段。更新时未出现字段保留旧值，显式 `null` 清除；`giftBox:false` 取消礼盒标记。

```json
{
  "sweetnessDescription": "清甜，未提供实测糖度",
  "seasonalMonths": [9, 10, 11],
  "giftBox": true,
  "useCases": ["FRIEND_GIFT", "FAMILY"]
}
```

甜度最多200字；月份为1–12整数，去重并排序；用途限定 DAILY、ELDER_GIFT、FRIEND_GIFT、BUSINESS_GIFT、FAMILY。空月份或用途表示未提供，不满足相应硬筛选。预算按所选规格单价乘用户明确的份数计算，缺省一份；最多返回三件当前上架、分类有效且库存足够的不同商品，没有匹配时不放宽条件。

## 已实现的管理接口

模型：GET `/models`、GET `/models/{id}`、POST `/models` 保存新版本草稿；POST `/models/{id}/activate`、POST `/models/{id}/disable`；POST `/models/{id}/test?capability=chat|stream|intent|embedding|all`。测试返回能力名称、success、elapsedMs、安全提示和向量维度。

删除：DELETE `/models/{id}`，仅 OWNER。响应 `{id,mode}`，`mode=DELETE` 表示无回答/索引引用且已永久删除，`mode=ARCHIVE` 表示存在历史引用、从列表隐藏且保留追溯。模型列表/详情返回 `deletionMode=DELETE|ARCHIVE|BLOCKED` 及 `deletionReason`，仅作为页面说明；服务端删除时重新加锁校验。当前激活、ACCEPTED/RUNNING 回答、BUILDING/READY/ACTIVE 向量代次或 PENDING/RUNNING 索引任务使用该版本时返回409并说明解除方式。已删除/隐藏版本的详情、继承草稿、测试、激活、关闭、新索引构建及再次删除返回404；历史问答的模型版本/名称和旧索引关联保留。操作审计分别为 `AI_MODEL_DELETE` / `AI_MODEL_ARCHIVE`。

配置请求包含 baseId、name、chatBaseUrl、chatModel、chatKey、clearChatKey、embeddingBaseUrl、embeddingModel、embeddingKey、clearEmbeddingKey、reuseChatKey、embeddingDimensions、promptTemplate、parameters。遗漏新 Key 保留 baseId 的密文；清除与提供新 Key 不能同时执行。reuseChatKey 将聊天密文复制到向量配置，不能同时设置或清除向量 Key。响应只有 hasChatKey/hasEmbeddingKey 及固定掩码，Key 不返回。

知识：GET `/knowledge?page=1&size=20&type=FAQ&keyword=保鲜`、GET `/knowledge/{id}`；POST/PUT 保存草稿；POST `/{id}/publish` 与 `/{id}/retry` 使用 `{version}`；PATCH `/{id}/enabled` 使用 `{version,enabled}`。POST `/knowledge/search-test` 使用 `{question}` 返回依据、关键词/向量分数、精确 FAQ 命中与检索模式。GET `/knowledge/business-policy` 返回实际系统限制供编辑政策时比较。

规则：GET `/rules`；POST/PUT 保存；PATCH `/{id}/enabled`；POST `/rules/test?scope=INPUT|OUTPUT` 使用 `{question}`。输入支持 LITERAL、ALL、ANY，输出仅支持 LITERAL；terms 最多20个，每个最多100字，按 priority 降序及 id 升序决定优先命中。

向量代次：GET `/models/profiles`；POST `/models/{configId}/profiles` 开始构建；POST `/models/profiles/{id}/switch` 原子切换，POST `/models/profiles/{id}/cancel` 取消构建。配置须已有激活快照及向量凭据，切换前验证所有当前有效修订的块向量完整。

## 数据迁移

保留 V1–V10 原始校验和；V9 添加商品属性和 AI 权限约束，V10 建立知识/修订/块/向量代次/索引任务、会话/消息/请求/来源/反馈、模型/规则/命中、问题/尝试事实与额度表。V11 添加模型 `deleted_at` 及可见列表索引，旧配置默认仍可见，密文、引用及既有数据保留。H2 test-schema 与迁移同步，正式升级通过 Flyway 执行，不在应用启动时下载模型或自动调用外部 API。

## 会话、发送及恢复

访客先 POST `/api/v1/public/ai/guest-session`，响应为 `{token,conversationId,expiresAt}`；后续通过 `X-AI-Guest-Token` 传入 token。BUYER 路径使用 Authorization Bearer JWT。以下相对路径挂在各身份的 `/ai` 下：

| 方法 / 路径 | 请求 / 返回 |
| --- | --- |
| POST `/conversations` | `{context}`，创建归属当前身份的会话 |
| GET `/conversations` | 买家历史分页；访客没有跨设备列表 |
| GET `/conversations/{id}` | `{conversation,messages}`，按稳定 sequence 排序 |
| DELETE `/conversations/{id}` | 立即删除正文、来源、上下文及身份关联，取消活动生成，匿名指标保留 |
| POST `/conversations/{id}/messages` | `{clientRequestKey,text,context}`，返回 Request，接受后30秒内订阅 |
| GET `/requests/{id}` | 读取真实状态，不启动模型 |
| GET `/requests/{id}/stream` | fetch SSE；一条活动尝试只能订阅一次；终态可重新读取 |
| POST `/requests/{id}/stop` | 幂等停止，返回终态；已完成时保持原结果 |
| POST `/requests/{id}/retry` | `{clientRequestKey}`，新 answerId、新 attempt，原 questionId不变 |
| PUT `/answers/{id}/feedback` | `{helpful,reason?}`，只允许本人 COMPLETED/DEGRADED 回答 |

Request 为 `{id,conversationId,questionId,answerId,status,scene,mode,errorCode,errorMessage,createdAt,finishedAt}`。同一个会话不能并行发新问题；相同key及相同payload返回原request，不增提问或调用；相同key不同payload返回409。主动重试不能重试活动或拦截结果。删除后迟到worker和回调不能重建内容。

`context` 仅支持 productId、orderId、afterSaleId、budget、month、giftBox、useCase、taste、quantity；买家身份始终来自JWT。商品/业务对象须通过服务端可见性/归属验证。订单号仅在本人范围检索；不存在及他人号码使用相同结果。多个业务候选展示选择卡片，不猜测对象；预算为所选规格单价乘份数，默认一份。结构化条件面板提交的新条件替换旧推荐条件，自然语言明确的新预算/月份/份数可继续修改条件。

场景为 GENERAL、PRODUCT、RECOMMEND、ORDER、AFTER_SALE。mode为LLM、FAQ、CATALOG、BUSINESS、DEGRADED；BUSINESS为确定性的本人业务或实际系统政策，不能修改订单、发起退款、审批或改库存。CATALOG读取当前公开商品事实；DEGRADED明确提示基础服务。订单/退款中的地址、姓名、手机号等不进入模型，第三方usage未返回时保持null。

## SSE 帧与终态

```text
event:status
data:{"requestId":42,"answerId":84,"questionId":83,"configId":2,"status":"RUNNING"}

event:delta
data:{"requestId":42,"text":"草莓建议冷藏保存。"}

event:cards
data:{"items":[]}

event:sources
data:{"items":[{"ordinal":1,"type":"KNOWLEDGE","id":3,"version":1,"title":"草莓保鲜","excerpt":"保持干燥，食用前清洗","path":null}]}

event:done
data:{"request":{"id":42,"conversationId":6,"questionId":83,"answerId":84,"status":"COMPLETED","scene":"PRODUCT","mode":"LLM","errorCode":null,"errorMessage":null},"content":"草莓建议冷藏保存。","promptTokens":null,"completionTokens":null}
```

示例ID只是说明；实际Request同时包含时间。status后有多个delta和保活heartbeat；cards/sources/done在终结时发送，done只发一次。checkpoint在正文发送前保存安全文本，保证停止/断线保留已显示片段。流前错误使用JSON及HTTP状态，流后错误通过终态done返回；错误文本不能拼接为成功回答。失败或停止回答不进入下一轮模型历史。

状态：ACCEPTED → RUNNING → COMPLETED / DEGRADED / FAILED / CANCELLED / BLOCKED；输入拦截可直接由ACCEPTED进入BLOCKED。超过首内容时间可降级，已发送正文后失败保持FAILED；不把基础答案拼到半截在线答案之后。未订阅和进程重启后的租约过期回收为FAILED，不自动重发外部调用。

## 记录、规则与统计

GET `/admin/ai/conversations` 支持 page/size、ownerKind、buyerId、scene、status、from/to（更新时间区间）、keyword（脱敏正文）及helpful。详情返回messages、attempts和ruleHits，含模型版本、usage、时延、终态原因、来源修订；每次读取留审计。

GET `/admin/ai/rules?page=1&size=20&scope=INPUT` 返回分页规则；匹配测试只使用启用规则。规则编辑有version校验，接受时固化整个启用快照。GET `/admin/ai/statistics?from=2026-11-01&to=2026-11-17` 返回真实问题/尝试/反馈计数、补零趋势、场景和安全高频主题，口径见 [统计说明](ai-statistics.md)。
