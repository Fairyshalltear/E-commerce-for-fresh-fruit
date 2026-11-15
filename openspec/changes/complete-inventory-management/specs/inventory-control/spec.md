# Spec Delta

## Purpose

以商品规格为单位提供管理员库存总览和可追溯的出入库作业，使实物库存、订单预留、可售库存、盘点差异和低库存预警保持一致。采购和退供使用简单单据，销售和客户退货沿用已有订单与售后业务来源。

## ADDED Requirements

### Requirement: Inventory uses a single SKU quantity model
系统 SHALL 按 SKU 保存非负整数的实物库存、预留库存和低库存阈值。可售库存 SHALL 等于实物库存减预留库存，实物库存 SHALL 始终不低于预留库存。数量表示该 SKU 的销售包装单位，不按重量小数、批次、效期或仓库拆分。管理员 SHALL 不得直接设置预留数量。

#### Scenario: One kilogram packages are counted as packages
- **WHEN** 某 1kg/袋规格实物库存为 30、预留库存为 8
- **THEN** 系统显示实物 30 袋、预留 8 袋、可售 22 袋，不将数量解释为任意小数重量

### Requirement: Administrators can overview and filter SKU inventory
系统 SHALL 提供管理员库存分页列表及汇总，展示商品名称、规格、分类、商品/SKU 状态、实物、预留、可售、阈值和低库存状态。系统 SHALL 支持商品名称/规格关键词、一级或二级分类、商品/SKU 状态和正常/低库存/可售为零的组合筛选；汇总 SHALL 对全部匹配 SKU 统计，不能仅统计当前页。下架、停用和逻辑删除数据 SHALL 可通过筛选查看并保留库存历史。

#### Scenario: Filtered totals cover all matching pages
- **WHEN** 管理员筛选某一级分类得到 25 个 SKU 并查看每页 20 条的第 1 页
- **THEN** 列表返回 20 条、总数 25，汇总统计该分类全部 25 个匹配 SKU，一级分类包含其二级分类

#### Scenario: Invalid and out of range pagination are handled
- **WHEN** 管理员请求非法页码或每页超过 100 条，或请求超过末页的合法页码
- **THEN** 非法参数返回校验错误；超过末页返回空记录并保留真实总数，前端分别显示错误或空态

### Requirement: Manual stock documents support drafts and immutable posting
采购入库和退供出库 SHALL 使用具有唯一单号的单据，记录类型、供应商名称、可选参考采购单号、原因/备注、多个不重复 SKU 和正整数数量、制单人及时间。草稿 SHALL 可修改或删除且不影响库存；管理员确认后 SHALL 一次性生效，无独立审批。生效单据 SHALL 保留确认人/时间及库存变化，不得修改或删除。失败 SHALL 保留草稿并不产生部分库存变更。

#### Scenario: Saving a purchase draft does not receive goods
- **WHEN** 管理员保存包含苹果 20 袋、梨 10 盒的采购入库草稿
- **THEN** 系统保存单据，但两个 SKU 的库存和成功库存流水均不变化

#### Scenario: Posted documents cannot be edited
- **WHEN** 管理员修改或删除已经确认生效的入库或退供单
- **THEN** 系统拒绝请求，原单据和库存保持原状，界面提示通过新增盘点调整记录纠正账面差异

#### Scenario: Duplicate lines and invalid quantities are rejected
- **WHEN** 单据包含重复 SKU、零数量、负数、小数或不存在的 SKU
- **THEN** 系统拒绝保存或确认并显示具体错误，不产生任何库存变化

### Requirement: Purchase receipt increases on-hand stock after confirmation
采购入库 SHALL 表示供应商货物已经到达并由管理员验收确认；确认生效时按明细数量增加实物库存，预留数量保持不变。该业务 SHALL 不自动创建商品，不管理采购订单、采购金额或结算。正常采购入库 SHALL 仅选择未逻辑删除商品的启用 SKU，商品下架 SHALL 不阻止备货；数量累加超出支持范围 SHALL 被拒绝。

#### Scenario: Receiving goods raises available inventory
- **WHEN** 某 SKU 实物 30、预留 8，管理员确认采购入库 20
- **THEN** 实物变为 50、预留仍为 8、可售变为 42，并保存与入库单关联的 +20 流水

#### Scenario: An unlisted product can be stocked
- **WHEN** 商品下架但未删除且 SKU 启用，管理员确认其采购入库
- **THEN** 系统允许入库，商品仍保持下架状态

### Requirement: Supplier return decreases only unreserved on-hand stock
退货出库 SHALL 表示商家将仓库货物退给供应商，界面 SHALL 使用“退供出库”避免与客户退货混淆。确认生效时 SHALL 减少实物库存并保留预留数量；出库数量不得超过当前可售数量。系统 SHALL 允许处理停用 SKU 或已逻辑删除商品的剩余库存，保留来源信息；不执行供应商退款或结算。

#### Scenario: Supplier return preserves buyer commitments
- **WHEN** 某 SKU 实物 30、预留 8，管理员确认退供 5
- **THEN** 实物变为 25、预留仍为 8、可售变为 17，并记录与退供单关联的 -5 流水

#### Scenario: Supplier return cannot consume reserved goods
- **WHEN** 某 SKU 实物 30、预留 8，管理员试图退供 23
- **THEN** 系统拒绝整张单据，提示最多可退供 22，所有明细库存和成功流水保持原状

### Requirement: Sale issue is generated exclusively by order dispatch
销售出库 SHALL 仅在商城订单实际发货成功时产生，按发货数量同时减少实物和预留库存，并关联订单及明细。下单预留、付款、取消或未发货退款 SHALL 不被记为实物出库。库存页面 SHALL 展示销售出库记录并可跳转订单处理，不提供手工线下销售或再次确认同一发货出库的入口。

#### Scenario: Order reservation is not a physical issue
- **WHEN** 某 SKU 实物为 30、预留为 0，买家下单购买 8 并随后付款
- **THEN** 实物仍为 30、预留为 8、可售为 22，不产生销售出库流水

#### Scenario: Dispatch reduces stock once
- **WHEN** 上述订单发货 8，随后管理员重复提交发货或在库存页面查看该记录
- **THEN** 首次发货使实物变为 22、预留变为 0、可售仍为 22；重复请求和查看不再次扣库存，销售出库可追溯订单

### Requirement: Customer return receipt follows after-sales acceptance
客户退货入库 SHALL 仅由已有售后等待收货申请的管理员验收产生，仅确认可重新销售的入库数量增加实物库存，预留数量保持不变。退款数量和入库数量 SHALL 独立记录且分别不超过获批数量。库存页面 SHALL 展示关联订单和售后单，跳转售后验收；不得通过手工入库再次增加同一售后库存。

#### Scenario: Partially resalable return is received
- **WHEN** 获批退货 5，管理员确认退款 5、可入库 3
- **THEN** 按订单价格处理 5 的模拟退款，实物仅增加 3，记录 +3 客户退货入库流水并关联售后明细

#### Scenario: Refund without physical goods does not add on-hand stock
- **WHEN** 管理员批准未发货退款，或验收客户退货时可入库数量为 0
- **THEN** 未发货退款仅释放对应预留库存；可入库为 0 的已退货处理不增加实物库存，两者均不产生客户退货入库数量流水

#### Scenario: A completed return is not received twice
- **WHEN** 管理员重复验收已完成的售后申请
- **THEN** 系统拒绝再次处理，不重复退款、不增加库存或重复创建入库流水

### Requirement: Inventory counts preserve a reviewable snapshot
盘点 SHALL 支持多 SKU 草稿，记录选取时的账面实物、预留、可售数量和库存版本，以及实盘非负整数数量、差异和原因。草稿 SHALL 不修改库存；确认生效 SHALL 将实物调整为实盘数，保留预留量。任一 SKU 自快照产生后发生库存版本变化 SHALL 拒绝整张确认并保留草稿，展示最新数据，要求管理员显式刷新复核；不得自动覆盖快照或实盘输入。

#### Scenario: Count confirms a loss
- **WHEN** 盘点快照实物 30、预留 8，实盘输入 27 且库存版本未变化
- **THEN** 确认后实物为 27、预留为 8、可售为 19，保存盘亏 -3 的调整前后数据、原因和操作人

#### Scenario: Unchanged quantity still creates a count record
- **WHEN** 实盘等于账面实物并成功确认
- **THEN** 保留零差异盘点记录，不产生数量为零的库存变化流水

#### Scenario: Count is below reserved quantity
- **WHEN** 当前预留为 8，管理员确认实盘为 7
- **THEN** 系统拒绝整张盘点，提示先处理相关预留订单，库存和成功流水保持原状

#### Scenario: Concurrent order changes invalidate the count snapshot
- **WHEN** 管理员保存盘点草稿后发生下单预留、发货、入库或其他库存版本变化
- **THEN** 确认返回冲突，保留实盘输入和原快照，要求刷新账面数据并复核后再次确认

### Requirement: Stock posting is atomic and idempotent
系统 SHALL 使一张单据或一次盘点的全部库存变更、生效状态、来源记录和成功审计原子提交。重复或并发确认同一已生效单据 SHALL 返回既有结果而不重复变更库存；携带旧草稿版本的修改或首次确认 SHALL 返回冲突。确认 SHALL 基于当前库存校验，任一明细不满足条件时全部回滚。

#### Scenario: One failed line rolls back the entire document
- **WHEN** 一张退供单含两个 SKU，第一个数量足够、第二个可售数量不足
- **THEN** 两个 SKU 均不扣减，单据仍为草稿，无成功出库流水或成功审计

#### Scenario: Two requests confirm the same draft
- **WHEN** 两个请求并发确认同一有效采购草稿
- **THEN** 只生效一次，两次成功响应均指向同一已生效单据，库存和来源流水只增加一次

### Requirement: Low stock alerts reflect current available inventory
系统 SHALL 允许管理员在库存模块设置非负整数阈值，并按当前可售库存严格低于正阈值标记低库存；等于阈值 SHALL 不预警，阈值 0 SHALL 关闭低库存预警。默认预警 SHALL 包含未删除商品的启用 SKU，下架商品仍可预警，停用或已删除 SKU 不计入预警。设置阈值 SHALL 校验库存版本、记录审计且不新增数量变化流水。

#### Scenario: Reservation alone triggers a low stock alert
- **WHEN** 实物 10、阈值 5，买家下单预留 6
- **THEN** 可售为 4，系统在后台预警查询中标记该 SKU 为低库存，即使尚未发货

#### Scenario: Threshold equality and zero clear the alert
- **WHEN** 可售从 4 恢复为阈值 5，或管理员将阈值改为 0
- **THEN** 后台低库存状态和预警数量解除，不产生重复提醒消息

### Requirement: Administrative alerts refresh without external messages
管理后台 SHALL 提供库存预警列表和库存菜单预警数量提示，在进入后台、相关操作成功及可见页面每 60 秒刷新当前预警；页面重新可见 SHALL 及时刷新。刷新失败 SHALL 显示状态并提供重试，不能将失败解释为零预警。系统 SHALL 不发送站内通知消息、邮件或短信，不保留通知已读/未读状态。

#### Scenario: Open admin page observes a buyer reservation
- **WHEN** 管理员停留在后台且买家下单使库存低于阈值
- **THEN** 后台在下一次成功的 60 秒刷新中更新菜单预警数量，预警页显示当前匹配 SKU

### Requirement: Stock movements are searchable and preserve sources
系统 SHALL 提供库存流水分页查询，支持商品/规格关键词、SKU、入/出方向、类型、单号和发生时间范围筛选，按发生时间及记录标识倒序返回。流水 SHALL 展示数量变化、前后实物、原因、操作人、时间和业务来源；采购、退供、销售、客户退货及盘点 SHALL 能追溯相关单据或订单/售后。初始库存、历史商品编辑调整 SHALL 保留并显示为独立类型，不伪造来源单据。

#### Scenario: Historical adjustments remain visible
- **WHEN** 管理员查看升级前已有的 INITIAL 或 PRODUCT_EDIT_ADJUSTMENT 流水
- **THEN** 显示原数量、原因和操作者，来源显示历史初始化或商品编辑调整，不伪造采购单号

#### Scenario: Renaming a product does not erase posted details
- **WHEN** 已生效单据对应的商品改名、规格改名、停用或逻辑删除
- **THEN** 原单据保留确认时的商品/规格快照和原来源关联，库存历史仍可查询

### Requirement: Inventory administration is role protected
库存查询、单据草稿、生效确认、盘点及阈值配置 SHALL 仅允许 ADMIN，操作人 SHALL 从服务端认证信息取得。前端 SHALL 阻止买家进入管理员库存路由，后端 SHALL 拒绝未认证或买家库存请求，不接受客户端覆盖操作人或预留数量。

#### Scenario: Buyer cannot create a stock receipt
- **WHEN** 买家调用库存采购入库或盘点确认接口
- **THEN** 系统返回权限拒绝，库存、单据和成功库存流水不变化
