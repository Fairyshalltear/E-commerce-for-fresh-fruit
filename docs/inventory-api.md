# 库存管理 API 与操作说明

所有接口以 `/api/v1/admin/inventory` 为前缀，只允许 ADMIN，操作人取 JWT。响应沿用 `code/message/data`；参数非法 400、未认证 401、无权限 403、不存在 404，版本/状态/库存不足 409。数量按 SKU 包装单位的整数计数，可售=实物−预留，不管理批次、效期、金额和供应商结算。

| 方法与路径 | 参数/行为 |
|---|---|
| GET `/stocks` | keyword、categoryId、status、skuStatus、deleted、skuId、stockState（NORMAL/LOW/ZERO）、page、size |
| GET `/summary` | 与 stocks 相同筛选，返回全部匹配 SKU 的数量汇总，不只当前页；件数不表示重量或资产金额 |
| GET `/sku-options` | 按 keyword、type、page、size 搜索作业规格；采购排除停用/删除对象，退供与盘点可处理剩余历史库存 |
| GET `/alerts/count` | 当前低库存 SKU 数量 |
| PATCH `/stocks/{skuId}/threshold` | `{ "lowStockThreshold": 5, "version": 0 }`，仅改阈值并记审计，不新增数量流水 |
| GET `/movements` | keyword、skuId、direction（IN/OUT）、type、sourceNo、from、to、page、size |
| GET `/documents` | type、status、keyword（单号/供应商/参考单号）、from、to、page、size |
| POST `/documents` | 创建草稿，见下方示例 |
| GET/PUT `/documents/{id}` | 查看详情、带 version 修改草稿；不能变更单据类型 |
| DELETE `/documents/{id}?version=0` | 删除对应版本草稿；已生效单据不可删改 |
| POST `/documents/{id}/post` | `{ "version": 0 }`，确认整单生效；重试已生效单据返回原结果，不重复库存变更 |
| POST `/documents/{id}/refresh-snapshot` | `{ "version": 0 }`，仅盘点草稿可显式刷新账面快照，保留实盘数 |

分页默认 20 条、范围 1–100、页码从 1 开始；超末页返回空页及真实总数。时间使用 ISO 本地时间 `2026-11-15T00:00:00`，起止区间含边界。默认库存总览排除已删除商品，包含下架商品和停用规格，`deleted=1` 可查删除对象历史。

## 采购与退供

```json
{
  "type": "PURCHASE_IN",
  "supplier": "果园合作社",
  "referenceNo": "人工参考采购单号",
  "reason": "到货验收",
  "items": [{ "skuId": 1, "quantity": 20 }]
}
```

单据 type 可选 `PURCHASE_IN`（采购入库）、`SUPPLIER_RETURN_OUT`（退供出库）、`STOCKTAKE`（盘点）。采购/退供必须填写供应商，退供/盘点必须填写原因，供应商和参考单号最长 100 字符，原因最长 200，单据明细 1–100 条且 SKU 不可重复。数量必须是正整数，小数拒绝。参考采购号仅人工追溯，不限制某批采购累计可退数量。

保存草稿不会变更库存。到货验收后确认采购，实物增加、预留不变。实际退给供应商时确认退供，实物减少、预留不变；退供最多等于当前可售数量。任一明细库存不足或数量溢出，整单回滚、保留草稿。单据创建/修改版本与库存数值版本分开，采购/退供确认基于当前库存计算。

确认后展示不可编辑详情，包括创建/确认人和时间、前后库存、差额、商品及规格快照。已生效单据数量或记账有误时，通过新的盘点调整纠正账面差异，保留原单据，不提供财务冲销。

## 盘点

```json
{
  "type": "STOCKTAKE",
  "reason": "日常实物核对",
  "items": [{ "skuId": 1, "countedQuantity": 27 }]
}
```

服务端生成账面实物/预留/可售及库存版本快照，客户端只填非负整数实盘，不接受伪造账面字段。修改草稿保留已选 SKU 原快照，新增行取得新快照。确认后实物变为实盘数，预留不变，差额生成盘盈/盘亏流水；差额为零仍保存盘点记录，不生成零差额流水。

若快照后发生预留、释放、发货、补货或阈值版本变化，返回 `409 INVENTORY_CONFLICT`，详情保留原账面/实盘并提供 `currentStock`。界面展示最新值，管理员显式“刷新账面数据并复核”后，再核对实盘并确认。刷新不能自动确认；实盘低于当前预留时必须先处理相关订单。

## 自动流水与预警

销售出库仅由订单发货产生 `ORDER_DISPATCH`：实物和预留一起扣减。下单增加预留，付款不改变库存，取消/未发货退款仅释放预留，不算实物出库或入库。客户退货仅由售后验收产生 `RETURN_RESTOCK`，退款量和可入库量独立，只有可重新销售部分回补库存；损坏商品可退款但不回补。库存页面查询来源并跳转订单/售后，不提供重复手工过账。

新流水结构化来源为 DOCUMENT、ORDER、AFTER_SALE，保留来源主/明细标识、单号及名称快照。INITIAL 与历史 PRODUCT_EDIT_ADJUSTMENT 独立显示。V6 仅补全能通过单号、SKU、数量唯一验证的旧订单/售后来源；不能确认则保留原因文本，不伪造历史名称快照。

低库存仅计未删除商品的启用 SKU，下架仍预警：阈值 > 0 且可售 < 阈值。等于阈值不预警，0 关闭。后台菜单及预警页在进入/恢复可见时、操作成功后、可见页面每 60 秒刷新；失败保留上次值并提示错误，不显示假零。此版本不发送邮件、短信或站内通知消息。

## 商品保存契约升级

新 SKU 请求只提交规格、价格、状态和初始 `onHand`，阈值默认 0。已有 SKU 只提交 id、规格、价格和状态，不再提交 onHand、lowStockThreshold、reserved、inventoryVersion；商品不再提交 adjustmentReason。已有库存字段在商品编辑中只读，改价/描述不受库存版本变更影响。旧字段负载拒绝且不覆盖库存。已有 INITIAL/PRODUCT_EDIT_ADJUSTMENT 历史完整保留。

## 验证命令

```sh
mvn -f back-end/pom.xml -Dtest=InventoryIntegrationTest test
mvn -f back-end/pom.xml test
npm --prefix front-end run typecheck
npm --prefix front-end run build
```

真实 MySQL 升级测试只使用专用空白可丢弃库，按 README 配置迁移测试环境，验证 V1–V6 升级、旧库存/交易/流水保留和重复迁移。页面入口 `/admin/inventory`，商品初始库存代表创建时已持有的货物，同一货物不可再次登记采购入库。
