# 订单、售后与评价 API

所有接口返回 `{ "code", "message", "data" }`。买家接口需要 BUYER JWT，管理接口需要 ADMIN JWT；账户身份由 JWT subject 决定，请求体不接受 buyer/admin ID。订单分页使用 `page`（从 1 开始）和 `size`（1–100）。金额以十进制字符串返回。

## 买家订单

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/buyer/orders/checkout-preview` | 地址和商品结算复核。`addressId`、`requestKey` 必填；购物车模式省略 `items`，立即购买传 `[{skuId, quantity}]`。 |
| POST | `/api/v1/buyer/orders` | 创建待付款订单。请求结构同预览；相同买家及 `requestKey` 重试返回原订单。 |
| GET | `/api/v1/buyer/orders?status=ALL&page=1&size=12` | 查询订单。状态可选 `ALL`、`PENDING_PAYMENT`、`PENDING_SHIPMENT`、`PENDING_RECEIPT`、`COMPLETED`、`CANCELLED`。 |
| GET | `/api/v1/buyer/orders/{id}` | 查询订单快照、状态事件及模拟物流轨迹，仅限本人。 |
| POST | `/api/v1/buyer/orders/{id}/payment` | 模拟支付：`channel=ALIPAY|WECHAT`、`result=SUCCESS|FAILED|CANCELLED`、唯一 `requestKey`。失败/取消保持待付款；成功转待发货。 |
| POST | `/api/v1/buyer/orders/{id}/cancel` | 仅待付款订单可取消并释放预留。已付款未发货请提交退款申请。 |
| POST | `/api/v1/buyer/orders/{id}/confirm-receipt` | 仅本人待收货订单可确认，状态转已完成。 |

订单提交时服务端重新校验地址、商品、当前价格、购物车接受价及库存。失败返回 400/409 且事务回滚。待付款超时由 `freshfruit.order.unpaid-timeout-minutes` 配置，默认 15 分钟。

## 管理订单与模拟物流

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/admin/orders?status=ALL&from=2026-01-01T00:00:00&to=2026-12-31T23:59:59&keyword=关键词&page=1&size=12` | 按状态、下单时间、订单号或买家用户名/电话筛选。时间为不含时区的 `Asia/Shanghai` 本地时间。 |
| GET | `/api/v1/admin/orders/{id}` | 查看订单快照及处理历史。 |
| POST | `/api/v1/admin/orders/{id}/dispatch` | 发货，`{ "carrier": "顺丰", "trackingNumber": "SF..." }`。成功扣减已发货数量的实物及预留库存并记录审计。 |
| POST | `/api/v1/admin/orders/{id}/tracking-events` | 添加模拟轨迹，`{ "description": "已揽收", "occurredAt": "2026-11-14T12:00:00" }`。仅允许已发货订单。 |

物流当前由本地持久化 `TrackingProvider` 提供，不请求任何承运商；真实供应商对接需另行实现 provider。

## 售后

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/buyer/orders/{id}/after-sales` | `type=REFUND|RETURN`，并提交 `reason`、可选 `description` 与 `items:[{orderItemId,quantity}]`。退款仅限待发货订单；退货仅限确认收货后 7 天内。 |
| GET | `/api/v1/buyer/after-sales` | 查看本人售后申请。 |
| GET | `/api/v1/buyer/after-sales/{id}` | 查看本人售后详情。 |
| POST | `/api/v1/buyer/after-sales/{id}/return-shipment` | 获批退货提交 `{ "carrier", "trackingNumber" }`。 |
| GET | `/api/v1/admin/after-sales?status=PENDING_REVIEW&type=REFUND&page=1&size=12` | 管理员按状态、类型分页筛选。状态：`PENDING_REVIEW`、`WAITING_RETURN`、`WAITING_RECEIPT`、`COMPLETED`、`REJECTED`。 |
| GET | `/api/v1/admin/after-sales/{id}` | 查看售后申请和订单明细。 |
| POST | `/api/v1/admin/after-sales/{id}/review` | `{ "approved": true, "reason": "审核说明" }`。拒绝也必须填写原因。退款批准模拟退款并释放相应预留；退货批准进入等待寄回。 |
| POST | `/api/v1/admin/after-sales/{id}/receive` | 每项提交 `{orderItemId, refundQuantity, restockQuantity}`；两个数量分别不得超过该申请数量。仅 `restockQuantity` 增加实物库存，退款金额按快照单价计算。 |

售后申请占用的明细数量在拒绝时释放；批准处理后不允许重复退款/收货。非本人资源返回 404，角色不符返回 403，状态冲突返回 409。

## 商品评价

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/buyer/reviews` | `{ "orderItemId": 123, "rating": 1, "content": "..." }`，订单须已完成且明细仅能评价一次。买家不能修改或删除。 |
| GET | `/api/v1/public/products/{productId}/reviews?page=1&size=10` | 公开有效评价分页与均分；不返回订单 ID、买家 ID 或审核字段。 |
| GET | `/api/v1/admin/reviews?page=1&size=20&deleted=false` | 管理员查看有效或已删除评价及审计原因。 |
| POST | `/api/v1/admin/reviews/{id}/delete` | `{ "reason": "违规内容" }`，逻辑删除并留存快照及操作审计；公开列表、总数和均分即时排除。 |

## 关键错误

- `400 VALIDATION_ERROR`：参数格式、评分、申请数量等校验失败。
- `401`：缺失或失效的登录令牌。
- `403`：角色无权访问。
- `404 NOT_FOUND`：资源不存在或不属于当前买家。
- `409 ORDER_CONFLICT` / `409 AFTER_SALE_CONFLICT`：状态变化、库存或数量竞争冲突；客户端应刷新服务端状态后再操作。
