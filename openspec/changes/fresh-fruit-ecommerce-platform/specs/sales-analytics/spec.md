# Spec Delta

## Purpose

让管理员按明确且可复核的口径查看平台经营情况，支持答辩演示日、周、月趋势及订单、用户和商品维度的基础统计。

## ADDED Requirements

### Requirement: Administrator views net sales and quantity trends
系统 SHALL 按日、周、月汇总销售额和销量。销售额 SHALL 仅计支付成功订单商品金额并扣除已完成退款金额；销量 SHALL 仅计支付成功商品数量并扣除已完成退货退款数量。未付款和已取消订单 SHALL 不计入销售额或销量。

#### Scenario: Refund is reflected in net sales
- **WHEN** 管理员查询包含已支付订单和已完成退款的统计周期
- **THEN** 系统从该周期净销售额和净销量中扣除对应退款金额和商品数量

### Requirement: Administrator views order and user statistics
系统 SHALL 按选定时间范围统计订单总量及订单状态分布、买家注册量和活跃买家数。活跃买家 SHALL 按统计周期内至少成功登录一次的去重买家计数。

#### Scenario: Administrator views monthly order distribution
- **WHEN** 管理员选择一个月度统计周期
- **THEN** 系统返回该周期订单量及各订单状态的数量

### Requirement: Administrator views product rankings
系统 SHALL 提供按净销售数量排序的热销商品排行和按可售库存从低到高排序的库存排行，并展示对应商品及 SKU 标识。

#### Scenario: Administrator views low-inventory ranking
- **WHEN** 管理员查看库存排行
- **THEN** 系统优先展示可售库存较低的 SKU，并允许管理员识别达到低库存阈值的 SKU

### Requirement: Analytics are restricted to administrators
系统 SHALL 仅向已认证管理员提供经营统计数据。

#### Scenario: Buyer requests analytics
- **WHEN** 未认证访客或买家请求管理员统计数据
- **THEN** 系统拒绝请求且不返回聚合数据
