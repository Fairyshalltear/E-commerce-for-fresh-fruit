# Spec Delta

## Purpose

以 SKU 为单位建立实物库存、预留库存和可售库存的一致口径，支持管理员在商品管理中设置初始库存并执行可追溯调整。本阶段保护库存预留和历史记录，为后续订单预留、发货扣减及完整库存作业提供基础，不引入批次或效期追踪。

## ADDED Requirements

### Requirement: Inventory is summarized by SKU
系统 SHALL 为每个 SKU 保存非负整数的实物库存、预留库存和低库存阈值，且始终满足实物库存不小于预留库存。可售库存 SHALL 等于实物库存减预留库存。新增 SKU 的预留库存 SHALL 初始化为 0；本阶段管理员商品接口 SHALL 不允许写入预留库存。

#### Scenario: Availability reflects reserved units
- **WHEN** SKU 实物库存为 20、预留库存为 7
- **THEN** 该 SKU 在商品详情显示可售库存为 13，后台分别展示 20、7 和 13

#### Scenario: Administrator attempts to overwrite reservations
- **WHEN** 商品编辑请求包含客户端指定的预留库存字段
- **THEN** 系统拒绝请求，不修改已有预留库存

### Requirement: Administrator adjusts on-hand stock atomically
系统 SHALL 允许管理员在商品新增及编辑中设置 SKU 实物库存。调整 SHALL 保留预留数量，拒绝小于当前预留库存的实物量，并与同次商品及 SKU 修改保持原子性。库存调整 SHALL 校验服务端当前值，禁止并发旧请求静默覆盖已完成的库存变更；冲突时要求重新获取数据。

#### Scenario: Adjustment below reserved stock is rejected
- **WHEN** SKU 实物库存为 10、预留库存为 4，管理员将实物库存调整为 3
- **THEN** 系统拒绝整次商品保存，商品信息、所有 SKU、库存和成功操作记录保持原状

#### Scenario: Another change occurred after the editor loaded
- **WHEN** 两位管理员读取相同版本后分别提交不同库存，第一份修改已经成功
- **THEN** 第二份旧版本请求返回冲突并保留第一份修改结果，提示刷新后再提交

#### Scenario: Adjustment to the reservation boundary succeeds
- **WHEN** 管理员将实物库存调整为恰好等于当前预留库存
- **THEN** 修改成功，可售库存为 0，预留库存保持不变

### Requirement: Initial stock and actual stock changes create durable movements
系统 SHALL 为正数初始库存及每次实际库存变化保存 SKU、调整类型、调整前后实物数量、差额、原因、管理员和时间。编辑调整未填写原因时 SHALL 使用“商品编辑调整”作为原因。未发生数量变化的保存 SHALL 不新增库存流水；业务保存失败 SHALL 不产生成功库存流水。

#### Scenario: Administrator changes stock from twelve to twenty
- **WHEN** 管理员将某 SKU 实物库存从 12 修改为 20 并成功保存
- **THEN** 系统保存一条前值 12、后值 20、差额 +8 的编辑调整流水及操作者和时间

#### Scenario: Product text changes without stock change
- **WHEN** 管理员仅编辑商品描述，库存数量保持原值
- **THEN** 商品描述成功保存，但不新增库存调整流水

### Requirement: Inventory data remains available for administration
管理员商品详情 SHALL 展示每个 SKU 的实物、预留、可售库存和低库存阈值，并在启用 SKU 可售库存低于阈值时标记低库存。停用或逻辑删除商品/SKU SHALL 保留历史库存及流水。本阶段 SHALL 不开放买家库存修改、独立入库/出库/盘点页面或库存批次管理。

#### Scenario: Available stock crosses the threshold
- **WHEN** SKU 的可售库存降至低库存阈值以下并随后恢复到阈值
- **THEN** 后台先显示低库存标记，恢复后清除标记；商城仍仅展示可售数量

#### Scenario: Buyer attempts an inventory modification
- **WHEN** 买家请求管理员商品或库存变更接口
- **THEN** 系统拒绝操作，所有库存保持不变
