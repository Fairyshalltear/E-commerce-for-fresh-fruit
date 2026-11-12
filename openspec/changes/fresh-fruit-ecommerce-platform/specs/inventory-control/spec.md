# Spec Delta

## Purpose

以 SKU 为最小库存单位记录可售量和实物量的变化，支持管理员执行日常出入库、盘点和低库存处理，而不引入批次追踪负担。

## ADDED Requirements

### Requirement: Inventory is summarized by SKU
系统 SHALL 为每个 SKU 维护实物库存、预留库存和低库存阈值，并按“实物库存减预留库存”计算可售库存。库存 SHALL 不按采购批次或到期日拆分追踪；水果的储存方法和保鲜说明属于商品信息。

#### Scenario: Buyer-facing availability reflects reservations
- **WHEN** 一个或多个未发货订单预留了某 SKU 的库存
- **THEN** 商品和结算界面展示扣除预留数量后的可售库存

### Requirement: Administrator records stock movements
系统 SHALL 允许管理员记录采购入库、销售出库、退货入库、退货出库及盘点调整，并保存 SKU、数量、原因、操作者和时间。正常订单的销售出库 SHALL 由发货流程产生且不得重复扣减。

#### Scenario: Administrator records returned goods
- **WHEN** 管理员确认退货商品可重新入库并登记其 SKU 和数量
- **THEN** 系统增加该 SKU 的实物库存并写入退货入库流水

#### Scenario: Inventory adjustment is auditable
- **WHEN** 管理员完成盘点并提交实际数量
- **THEN** 系统记录调整前后数量、盘点差异、原因及操作者

### Requirement: Inventory counts preserve reservations
系统 SHALL 在盘点调整时保留未履约订单的预留数量，并拒绝导致实物库存低于预留库存的调整。

#### Scenario: Count is below reserved quantity
- **WHEN** 管理员提交的盘点数量低于该 SKU 当前预留数量
- **THEN** 系统拒绝调整并要求先处理相关预留订单

### Requirement: Low inventory is visible to administrators
系统 SHALL 在 SKU 可售库存低于其配置阈值时向管理员展示低库存预警，并在库存恢复到阈值或以上时清除该预警。

#### Scenario: Available inventory crosses the threshold
- **WHEN** 一个 SKU 的可售库存低于其低库存阈值
- **THEN** 管理员库存总览将该 SKU 标记为低库存
